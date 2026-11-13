package com.translator.screen.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.translator.screen.R
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.translator.screen.extract.IScreenTextExtractor
import com.translator.screen.extract.ScreenTextExtractor
import com.translator.screen.extract.TranslatedTextRegion
import com.translator.screen.translate.ITranslationEngine
import com.translator.screen.translate.MlKitTranslationEngine
import com.translator.screen.util.PackageUtils
import com.translator.screen.util.LanguagePrefs
import com.translator.screen.util.TranslationHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import kotlinx.coroutines.CancellationException

/**
 * Accessibility service that drives the screen-translation pipeline.
 *
 * Lifecycle:
 *  - [onServiceConnected] publishes the singleton [instance], constructs the
 *    translation engine and overlay manager, and warms up ML Kit.
 *  - [onDestroy] tears everything down and clears [instance].
 *
 * Two entry points trigger work:
 *  - [QuickTileService.onClick] calls [triggerTranslation] to snapshot the
 *    current application window, translate its text regions, and show the
 *    overlay.
 *  - [onAccessibilityEvent] auto-dismisses the overlay when the user scrolls
 *    inside the translated app or surfaces a new top-level window.
 *
 * The class publishes a small public API ([instance], [isReady],
 * [isOverlayShowing], [triggerTranslation], [dismissOverlayPublic]) so the
 * Quick Settings tile can synchronously inspect and drive the service without
 * binding to it.
 */
class TranslatorAccessibilityService : AccessibilityService() {

    private var engine: ITranslationEngine? = null
    private var extractor: IScreenTextExtractor = ScreenTextExtractor()
    private var overlayManager: OverlayManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var overlayShownTimestampMs: Long = 0L

    @Volatile
    private var lastTranslatedTargetPackage: String? = null

    @Volatile
    private var isLocked: Boolean = false

    // ----- Continuous (live) translation mode -----
    @Volatile
    private var continuousMode: Boolean = false

    @Volatile
    private var continuousTargetPackage: String? = null

    private val continuousHandler = Handler(Looper.getMainLooper())
    private val retranslateRunnable = Runnable { retranslate() }

    // Auto-hide (v2.1.0 Phase 4): if no retranslation happens for [AUTO_HIDE_IDLE_MS]
    // in continuous mode, the overlay is dismissed so it does not linger over a stale
    // screen after the user walks away. Reset on each successful retranslate.
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable {
        Log.i(TAG, "Auto-hide: continuous mode idle for ${AUTO_HIDE_IDLE_MS}ms; stopping")
        Toast.makeText(
            applicationContext,
            getString(R.string.auto_hide_stopped),
            Toast.LENGTH_SHORT,
        ).show()
        stopContinuousMode()
    }

    // -------------------------------------------------------------------------
    // AccessibilityService lifecycle
    // -------------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val sourceTag = LanguagePrefs.getSourceTag(applicationContext)
        val targetTag = LanguagePrefs.getTargetTag(applicationContext)
        engine = MlKitTranslationEngine(sourceTag, targetTag).also { it.warmup() }
        overlayManager = OverlayManager(applicationContext) { onOverlayCloseRequested() }
        createNotificationChannel()
        Log.i(TAG, "Service connected, engine warmed up ($sourceTag -> $targetTag)")
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.close()
        engine = null
        overlayManager?.dismiss()
        overlayManager = null
        serviceScope.cancel()
        instance = null
        Log.i(TAG, "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        // Ignore events from our own package (overlay interactions, service UI).
        if (pkg == packageName?.toString()) return
        // Ignore SystemUI shade / status-bar events to avoid noise.
        if (pkg == "com.android.systemui") return
        // Only react once an overlay is actually shown.
        if (overlayManager?.isShowing != true) return

        // Continuous (live) mode: debounce + re-translate on content/scroll/text changes
        // in the target app, so the overlay updates smoothly as on-screen text changes
        // (e.g. a chat where new German messages arrive). (v1.6.0.)
        if (continuousMode && pkg == continuousTargetPackage) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    continuousHandler.removeCallbacks(retranslateRunnable)
                    continuousHandler.postDelayed(retranslateRunnable, CONTINUOUS_DEBOUNCE_MS)
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // A new top-level window in the target app (dialog / activity transition) —
                    // re-translate immediately rather than dismiss.
                    if (SystemClock.uptimeMillis() - overlayShownTimestampMs >= OVERLAY_GRACE_PERIOD_MS) {
                        continuousHandler.removeCallbacks(retranslateRunnable)
                        continuousHandler.postDelayed(retranslateRunnable, 0L)
                    }
                }
            }
            return
        }

        // One-shot mode: dismiss on scroll / window-state-change (existing behavior).
        val now = SystemClock.uptimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // A scroll inside the app we just translated invalidates cached
                // node bounds — dismiss so the user can re-trigger if desired.
                if (pkg == lastTranslatedTargetPackage) dismissOverlay()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // The shade-collapse animation produces a transient
                // WINDOW_STATE_CHANGED right after we dismiss the shade to
                // show the overlay; ignore it during the grace period so we
                // don't immediately tear down what we just built.
                if (now - overlayShownTimestampMs < OVERLAY_GRACE_PERIOD_MS) return
                // After the grace period any WINDOW_STATE_CHANGED — either the
                // same app swapping its top window (e.g. dialog, activity
                // transition) or the user app-switching to something else —
                // invalidates the overlay's spatial assumptions. Dismiss.
                dismissOverlay()
            }
            else -> {
                // Other event types are not auto-dismiss triggers.
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    // -------------------------------------------------------------------------
    // Public API used by QuickTileService and MainActivity
    // -------------------------------------------------------------------------

    /**
     * Snapshot the current top application window, translate every text region
     * found in its accessibility tree, and show the overlay.
     *
     * Safe to call from the main thread; work is dispatched onto
     * [Dispatchers.Default] inside [serviceScope]. A failed translation for
     * one region does not cancel the scope (SupervisorJob).
     */
    fun triggerTranslation() {
        if (engine == null) {
            Log.w(TAG, "triggerTranslation called before engine ready; ignoring")
            return
        }
        serviceScope.launch(Dispatchers.Default) {
            val targetWindow = pickTargetWindow() ?: run {
                Log.i(TAG, "No target application window found")
                return@launch
            }
            val root = targetWindow.root ?: run {
                Log.i(TAG, "Target window has no root AccessibilityNodeInfo")
                return@launch
            }
            val regions = withContext(Dispatchers.Default) { extractor.extract(root) }
            // Recycle the root node on API 31-32 (the extractor recycles children it obtains
            // via getChild, but not the root it's handed). API 33+ pools nodes and recycle()
            // is a no-op. (Fresh-eyes review P1-14.)
            if (Build.VERSION.SDK_INT < 33) {
                try { root.recycle() } catch (_: Throwable) { }
            }
            if (regions.isEmpty()) {
                Log.i(TAG, "No text regions found")
                return@launch
            }
            val target = regions.first().packageName
            lastTranslatedTargetPackage = target
            // Dismiss the notification shade AFTER snapshotting the node tree
            // so node references don't go stale mid-walk when the shade
            // collapse animates and invalidates window roots.
            withContext(Dispatchers.Main) {
                // GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE requires API 30 (Android 11).
                // On API 24-29 the shade won't auto-collapse; the user dismisses it manually
                // and the overlay appears once it's gone. (v1.4.0: minSdk 24 -> Android 7+.)
                if (Build.VERSION.SDK_INT >= 30) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    Log.i(TAG, "Dismiss-shade action unavailable on API ${Build.VERSION.SDK_INT}")
                }
            }
            val translated = regions.mapNotNull { reg ->
                val tr: String? = try {
                    engine?.translate(reg.text).also { result ->
                        when {
                            result == null ->
                                Log.d(TAG, "Translation skipped: engine null")
                            result == reg.text ->
                                Log.d(TAG, "Translation unchanged (text length=${reg.text.length})")
                            else ->
                                Log.d(
                                    TAG,
                                    "Translation hit (text length=${reg.text.length}, result length=${result.length})",
                                )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Translation failed for region: ${e.message}")
                    null
                }
                if (tr == null || tr == reg.text) null else TranslatedTextRegion(reg, tr)
            }
            if (translated.isEmpty()) {
                Log.i(TAG, "Nothing translated")
                return@launch
            }
            logToHistory(target, translated)
            withContext(Dispatchers.Main) {
                overlayManager?.show(translated)
                overlayShownTimestampMs = SystemClock.uptimeMillis()
                requestTileListeningState()
            }
        }
    }

    /** True iff the overlay is currently visible on screen. */
    fun isOverlayShowing(): Boolean = overlayManager?.isShowing == true

    /** Public dismiss entry point for the Quick Settings tile's toggle-off path. */
    fun dismissOverlayPublic() {
        stopContinuousMode()
    }

    /**
     * Toggle continuous (live) translation mode. Called by the Quick Settings tile:
     * tap once to start live translation (overlay persists + updates as on-screen text
     * changes); tap again (or the ✕ close button) to stop. This is the primary paradigm.
     */
    fun toggleContinuousMode() {
        if (continuousMode || overlayManager?.isShowing == true) {
            stopContinuousMode()
        } else {
            startContinuousMode()
        }
    }

    /** Start continuous (live) translation of the current foreground app. */
    fun startContinuousMode() {
        if (engine == null) {
            Log.w(TAG, "startContinuousMode: engine not ready")
            return
        }
        serviceScope.launch(Dispatchers.Default) {
            // Model-downloaded check — the #1 cause of "tap the tile, nothing happens".
            val modelReady = try { engine?.isModelDownloaded() ?: false } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            if (!modelReady) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        applicationContext.getString(R.string.model_not_downloaded_toast),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                Log.w(TAG, "startContinuousMode: model not downloaded; aborting")
                return@launch
            }
            retranslate(initial = true)
        }
    }

    /** Stop continuous mode + dismiss the overlay + cancel the notification. */
    fun stopContinuousMode() {
        continuousHandler.removeCallbacks(retranslateRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        continuousMode = false
        continuousTargetPackage = null
        cancelContinuousNotification()
        dismissOverlay()
    }

    /** Show a persistent notification while continuous (live) mode is active, so the user
     *  knows translation is running + can see it in the shade. (v1.9.0.)
     *  v2.1.0: added a "Stop" action button so the user can stop live translation from the
     *  shade without pulling down to the tile. */
    private fun showContinuousNotification() {
        val stopIntent = Intent(this, StopTranslationReceiver::class.java)
            .setAction(StopTranslationReceiver.ACTION_STOP_TRANSLATION)
        // FLAG_IMMUTABLE is required on API 31+ and safe on older versions.
        val stopPendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CONTINUOUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_translate_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.continuous_mode_active))
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.notification_action_stop),
                stopPendingIntent,
            )
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(CONTINUOUS_NOTIFICATION_ID, notification)
    }

    private fun cancelContinuousNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(CONTINUOUS_NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONTINUOUS_NOTIFICATION_CHANNEL_ID,
                getString(R.string.continuous_mode_active),
                NotificationManager.IMPORTANCE_LOW,
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    /**
     * Snapshot the current foreground app, translate, and show (or smoothly update) the
     * overlay. In continuous mode this is called debounced on each content change so the
     * overlay updates live without flicker ([OverlayManager.update] replaces in-place).
     */
    private fun retranslate(initial: Boolean = false) {
        serviceScope.launch(Dispatchers.Default) {
            val targetWindow = pickTargetWindow() ?: run {
                Log.d(TAG, "retranslate: no target window")
                if (initial) withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.diag_no_window), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val root = targetWindow.root ?: run {
                Log.d(TAG, "retranslate: no root")
                if (initial) withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.diag_no_content), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val regions = withContext(Dispatchers.Default) { extractor.extract(root) }
            if (Build.VERSION.SDK_INT < 33) { try { root.recycle() } catch (_: Throwable) {} }
            if (regions.isEmpty()) {
                Log.d(TAG, "retranslate: no regions")
                if (initial) withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.diag_no_text), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val target = regions.first().packageName
            if (initial) {
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    }
                }
            }
            val translated = regions.mapNotNull { reg ->
                val tr: String? = try {
                    engine?.translate(reg.text)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "retranslate: translate failed: ${e.message}")
                    null
                }
                if (tr == null || tr == reg.text) null else TranslatedTextRegion(reg, tr)
            }
            if (translated.isEmpty()) {
                Log.d(TAG, "retranslate: nothing translated")
                if (initial) withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.diag_nothing_translated), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            logToHistory(target, translated)
            withContext(Dispatchers.Main) {
                if (initial || overlayManager?.isShowing != true) {
                    overlayManager?.show(translated)
                    overlayShownTimestampMs = SystemClock.uptimeMillis()
                    continuousMode = true
                    continuousTargetPackage = target
                    requestTileListeningState()
                    if (initial) showContinuousNotification()
                } else {
                    // Smooth in-place update (no dismiss+re-add flicker).
                    overlayManager?.update(translated)
                }
                // Reset the idle auto-hide timer on every successful update.
                scheduleAutoHide()
            }
        }
    }

    /**
     * Live-switch the translation target language (called by MainActivity when the user
     * changes the dropdown). Closes the old engine and creates a new one with the new
     * target; the next [triggerTranslation] uses it. No-op if [tag] matches the current
     * target or the service isn't connected.
     */
    fun updateTargetLanguage(tag: String) {
        val current = engine?.targetLanguageTag
        if (current == tag) {
            Log.i(TAG, "updateTargetLanguage: already $tag; no-op")
            return
        }
        Log.i(TAG, "updateTargetLanguage: $current -> $tag")
        engine?.close()
        engine = MlKitTranslationEngine(LanguagePrefs.getSourceTag(applicationContext), tag).also { it.warmup() }
    }

    /**
     * Live-switch the source language (called by MainActivity). (v2.0.1: any-to-any.)
     */
    fun updateSourceLanguage(tag: String) {
        val current = engine?.sourceLanguageTag
        if (current == tag) {
            Log.i(TAG, "updateSourceLanguage: already $tag; no-op")
            return
        }
        Log.i(TAG, "updateSourceLanguage: $current -> $tag")
        engine?.close()
        engine = MlKitTranslationEngine(tag, LanguagePrefs.getTargetTag(applicationContext)).also { it.warmup() }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Pick the topmost application window to translate. Excludes our own package
     * and launcher windows (when another app window exists) so we don't translate
     * the home screen behind a dialog.
     *
     * NOTE: deliberately does NOT filter by [AccessibilityWindowInfo.isActive] —
     * the user taps the Quick Settings tile from the notification shade, so the
     * target app window is NOT active at snapshot time (the shade is). Filtering
     * by isActive silently returned null and the core feature no-opped from its
     * only entry point. The app window is still in [windows] while the shade is
     * open; pick it by layer instead. (Fresh-eyes review P0-1.)
     */
    private fun pickTargetWindow(): AccessibilityWindowInfo? {
        val all = windows ?: return null
        val ownPkg = packageName?.toString().orEmpty()
        // Cache the launcher package once per process — PackageUtils.resolveLauncherPackage
        // does a synchronous PackageManager.resolveActivity binder call; calling it 2N times
        // per pick was wasteful. (Fresh-eyes review P1-15.)
        val launcherPkg = cachedLauncherPackage
        fun isLauncher(w: AccessibilityWindowInfo): Boolean {
            val pkg = w.root?.packageName?.toString().orEmpty()
            return pkg.isNotEmpty() && pkg == launcherPkg
        }
        val candidates = all.filter { w ->
            w.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                w.root?.packageName?.toString() != ownPkg
        }
        if (candidates.isEmpty()) return null
        val pool = if (candidates.size > 1 && candidates.any { isLauncher(it) }) {
            val nonLauncher = candidates.filterNot { isLauncher(it) }
            if (nonLauncher.isNotEmpty()) nonLauncher else candidates
        } else {
            candidates
        }
        return pool.sortedByDescending { it.layer }.firstOrNull()
    }

    /** Cached default-launcher package name; resolved lazily once per process. */
    private val cachedLauncherPackage: String? by lazy {
        PackageUtils.resolveLauncherPackage(applicationContext)
    }

    /**
     * Hide the overlay and reset the grace-period timestamp, then ask the
     * Quick Settings tile to re-query its state so it flips back to INACTIVE.
     */
    private fun dismissOverlay() {
        overlayManager?.dismiss()
        overlayShownTimestampMs = 0L
        requestTileListeningState()
    }

    /**
     * Ask the Quick Settings tile to re-query its state. TileService.requestListeningState
     * requires API 28 (Android 9); on API 24-27 the tile is always listening (ACTIVE_TILE
     * metadata is ignored), so the call is unnecessary + would NoSuchMethodError. (v1.4.0.)
     */
    private fun requestTileListeningState() {
        if (Build.VERSION.SDK_INT >= 28) {
            android.service.quicksettings.TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, QuickTileService::class.java),
            )
        }
    }

    /** Invoked by [OverlayManager] when the user taps the close button. */
    private fun onOverlayCloseRequested() {
        dismissOverlay()
    }

    /**
     * Persist up to [HISTORY_BATCH_LIMIT] of the most recent translations for this batch
     * to the on-device history (Phase 4). Called on the background dispatcher; the store
     * is thread-safe + dedupes consecutive identical entries, so live mode does not flood
     * the buffer with the same translated chat message.
     */
    private fun logToHistory(appPackage: String, translated: List<TranslatedTextRegion>) {
        translated.take(HISTORY_BATCH_LIMIT).forEach { reg ->
            TranslationHistory.add(applicationContext, reg.source.text, reg.translated, appPackage)
        }
    }

    /** (Re)schedule the continuous-mode idle auto-hide. Main-thread only. */
    private fun scheduleAutoHide() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_IDLE_MS)
    }

    private fun isEngineReady(): Boolean = engine != null

    companion object {
        private const val TAG = "TranslatorAccessibilityService"
        private const val OVERLAY_GRACE_PERIOD_MS = 350L
        private const val CONTINUOUS_DEBOUNCE_MS = 400L
        private const val CONTINUOUS_NOTIFICATION_CHANNEL_ID = "screen_translator_continuous"
        private const val CONTINUOUS_NOTIFICATION_ID = 1
        private const val HISTORY_BATCH_LIMIT = 5
        private const val AUTO_HIDE_IDLE_MS = 90_000L

        @Volatile
        var instance: TranslatorAccessibilityService? = null
            private set

        fun isReady(): Boolean = instance?.isEngineReady() == true
    }
}
