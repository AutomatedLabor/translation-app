package com.translator.screen.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.translator.screen.R
import com.translator.screen.extract.TranslatedTextRegion
import kotlin.math.abs

/**
 * Owns two [WindowManager] windows:
 *  1. A full-screen, non-touchable [TranslationCardView] that paints every
 *     [TranslatedTextRegion] as a rounded card with auto-fit translated text.
 *  2. A small tappable [CloseButtonView] anchored to the top-end corner that
 *     routes taps to [onCloseRequested].
 *
 * Both windows use [LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] so they may be
 * added without the SYSTEM_ALERT_WINDOW permission when the host accessibility
 * service is enabled.
 */
class OverlayManager(
    private val context: Context,
    private val onCloseRequested: () -> Unit,
) {

    private val tag = "OverlayManager"

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity
    private val Int.dp: Int get() = (this * density + 0.5f).toInt()
    // Use scaledDensity (not density) so overlay text respects the user's font-size
    // accessibility setting. (Fresh-eyes review P1-11.)
    private val Int.sp: Float get() = this * scaledDensity
    private val screenWidth: Int get() = context.resources.displayMetrics.widthPixels
    private val screenHeight: Int get() = context.resources.displayMetrics.heightPixels

    private var translationView: TranslationCardView? = null
    private var closeView: CloseButtonView? = null
    private var translationParams: LayoutParams? = null
    private var closeParams: LayoutParams? = null

    /** Side length of the draggable close (✕) button, in px. (v2.1.0: drag.) */
    private val closeButtonSize = 48.dp

    private val darkTheme: Boolean
        get() =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    /** Show (or replace) overlay with the given translated regions. Auto-dismisses any previous content first. */
    fun show(regions: List<TranslatedTextRegion>) {
        Log.d(tag, "show regions=${regions.size}")
        mainHandler.post {
            if (isShowing) dismissInternal()
            val tv = TranslationCardView(context, regions, darkTheme)
            val cv = CloseButtonView(context)
            val tp = buildTranslationParams()
            val cp = buildCloseParams()
            try {
                windowManager.addView(tv, tp)
            } catch (e: WindowManager.BadTokenException) {
                Log.e(tag, "BadTokenException adding translation overlay", e)
                return@post
            } catch (t: Throwable) {
                Log.e(tag, "Unexpected failure adding translation overlay", t)
                return@post
            }
            // Track tv IMMEDIATELY after addView succeeds, so any failure between the two
            // addView calls (or any later throw) doesn't leak tv in the WindowManager
            // untracked — the old code only assigned translationView after both addViews,
            // so a non-BadToken throw from addView(cv) left tv stuck on screen until
            // process death. dismissInternal() now removes it. (Fresh-eyes review P1-2.)
            translationView = tv
            translationParams = tp
            try {
                windowManager.addView(cv, cp)
            } catch (e: WindowManager.BadTokenException) {
                Log.e(tag, "BadTokenException adding close button overlay", e)
                dismissInternal()
                return@post
            } catch (t: Throwable) {
                Log.e(tag, "Unexpected failure adding close button overlay", t)
                dismissInternal()
                return@post
            }
            closeView = cv
            closeParams = cp
        }
    }

    /** Remove both windows from the WindowManager. No-op if not shown. */
    fun dismiss() {
        Log.d(tag, "dismiss")
        // Run inline when already on the main thread (e.g. from QuickTileService.onClick,
        // which calls dismissOverlayPublic -> dismiss) so isShowing flips synchronously
        // before refreshTileState queries it. Otherwise post to the main looper.
        // (Fresh-eyes review P1-12: previously always async, the tile showed stale STATE_ACTIVE.)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal()
        } else {
            mainHandler.post { dismissInternal() }
        }
    }

    /** Smoothly update the translation cards in-place (no dismiss+re-add flicker). */
    fun update(regions: List<TranslatedTextRegion>) {
        Log.d(tag, "update regions=${regions.size}")
        mainHandler.post {
            translationView?.updateRegions(regions)
        }
    }

    /** True iff at least the primary translation window is attached. */
    val isShowing: Boolean
        get() = translationView != null

    private fun dismissInternal() {
        translationView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: IllegalArgumentException) {
                // View was not attached / already removed; safe to ignore.
            }
        }
        translationView = null
        closeView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: IllegalArgumentException) {
                // View was not attached / already removed; safe to ignore.
            }
        }
        closeView = null
        translationParams = null
        closeParams = null
    }

    private fun buildTranslationParams(): LayoutParams =
        LayoutParams().apply {
            type = LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = (
                LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCHABLE or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            setTitle("ScreenTranslatorOverlay")
        }

    private fun buildCloseParams(): LayoutParams {
        val size = closeButtonSize
        return LayoutParams().apply {
            type = LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = LayoutParams.FLAG_NOT_FOCUSABLE
            width = size
            height = size
            // v2.1.0: TOP|START + explicit x/y so the button can be dragged (updateViewLayout
            // mutates x/y). Initial position = top-right corner (same visual as the old
            // TOP|END x=0 placement) so existing users see no change until they drag.
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - size).coerceAtLeast(0)
            y = 0
            // Match the translation layer's cutout mode so the button isn't inset away
            // from the translation card's top-end on punch-hole devices. (P1-7.)
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            setTitle("ScreenTranslatorCloseButton")
        }
    }

    /**
     * Single full-screen view that draws every translation card on one canvas.
     * Hosted in the non-touchable primary overlay window.
     */
    private inner class TranslationCardView(
        context: Context,
        private var regions: List<TranslatedTextRegion>,
        private val darkTheme: Boolean,
    ) : View(context) {

        init {
            // v2.1.0 (Phase 6): give TalkBack a readable description of the overlay's
            // contents (the translated texts), so the overlay is not announced as a
            // silent graphic. Updated in [updateRegions] as the live text changes.
            contentDescription = buildDescription()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        /** Smoothly replace the regions + redraw, without dismiss+re-add (no flicker). */
        fun updateRegions(newRegions: List<TranslatedTextRegion>) {
            regions = newRegions
            contentDescription = buildDescription()
            invalidate()
        }

        /** Concatenate the translated texts into one TalkBack-readable string. */
        private fun buildDescription(): String {
            if (regions.isEmpty()) {
                return getContext().getString(R.string.overlay_empty_description)
            }
            return regions.joinToString(separator = ". ") { it.translated.trim() }
        }

        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        private val cornerRadius = 12.dp.toFloat()
        private val pad8 = 8.dp.toFloat()
        private val pad6 = 6.dp.toFloat()
        private val pad12 = 12.dp.toFloat()
        private val pad16 = 16.dp.toFloat()
        private val minTextSizePx = 10.sp
        private val maxTextSizePx = 48.sp

        private val cardRect = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(screenWidth, screenHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            cardPaint.color = (if (darkTheme) 0xF0222422L else 0xF0FFFFFFL).toInt()
            textPaint.color = (if (darkTheme) 0xFFFFFFFFL else 0xFF1A1C19L).toInt()

            for (region in regions) {
                val text = region.translated.trim()
                if (text.isEmpty()) continue
                val b = region.bounds
                if (b.isEmpty) continue

                cardRect.set(
                    b.left.toFloat(),
                    b.top.toFloat(),
                    b.right.toFloat(),
                    b.bottom.toFloat(),
                )
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

                val availW = (b.width.toFloat() - pad16).coerceAtLeast(1f)
                val availH = (b.height.toFloat() - pad12).coerceAtLeast(1f)
                if (availW <= 1f || availH <= 1f) continue

                val layout = autoFitLayout(text, availW, availH)
                canvas.save()
                canvas.translate(b.left + pad8, b.top + pad6)
                layout.draw(canvas)
                canvas.restore()
            }
        }

        /**
         * Starts at min(availH, 48sp) and shrinks by 1px until both the layout
         * height fits within [availH] and every line fits within [availW],
         * stopping at 10sp.
         */
        private fun autoFitLayout(text: String, availW: Float, availH: Float): StaticLayout {
            val maxWidthPx = availW.toInt().coerceAtLeast(1)
            var textSize = minOf(availH, maxTextSizePx)
            if (textSize < minTextSizePx) textSize = minTextSizePx
            var layout = buildLayout(text, textSize, maxWidthPx)
            while (
                textSize > minTextSizePx &&
                (layout.height > availH || maxLineWidth(layout) > availW)
            ) {
                textSize -= 1f
                if (textSize < minTextSizePx) textSize = minTextSizePx
                layout = buildLayout(text, textSize, maxWidthPx)
                if (textSize == minTextSizePx) break
            }
            return layout
        }

        private fun buildLayout(text: String, size: Float, widthPx: Int): StaticLayout {
            textPaint.textSize = size
            return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        }

        private fun maxLineWidth(layout: StaticLayout): Float {
            var w = 0f
            for (i in 0 until layout.lineCount) {
                w = maxOf(w, layout.getLineWidth(i))
            }
            return w
        }
    }

    /**
     * A circular close button. Hosted in its own tappable overlay window
     * (FLAG_NOT_FOCUSABLE only — touch events reach this view).
     *
     * v2.1.0 (Phase 4): the button is **draggable**. A tap (movement within
     * [touchSlop]) closes the overlay; a drag repositions the button within the screen
     * bounds (via `WindowManager.updateViewLayout`) and does NOT close, so the user can
     * move the ✕ out of the way of translated content.
     */
    private inner class CloseButtonView(context: Context) : View(context) {

        init {
            // TalkBack label — R.string.close_overlay was defined but unused. (P1-6.)
            contentDescription = context.getString(R.string.close_overlay)
        }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xCC1B6B3A.toInt()
        }
        private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 3.dp.toFloat()
            strokeCap = Paint.Cap.ROUND
        }

        private val c12 = 12.dp.toFloat()
        private val c24 = 24.dp.toFloat()
        private val c36 = 36.dp.toFloat()

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        // Drag state. A tap (movement < touchSlop) closes; a drag repositions + does not.
        private var dragStartRawX = 0f
        private var dragStartRawY = 0f
        private var dragStartParamsX = 0
        private var dragStartParamsY = 0
        private var isDragging = false

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(closeButtonSize, closeButtonSize)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawCircle(c24, c24, c24, bgPaint)
            canvas.drawLine(c12, c12, c36, c36, xPaint)
            canvas.drawLine(c36, c12, c12, c36, xPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = event.rawX
                    dragStartRawY = event.rawY
                    dragStartParamsX = closeParams?.x ?: 0
                    dragStartParamsY = closeParams?.y ?: 0
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartRawX
                    val dy = event.rawY - dragStartRawY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val params = closeParams ?: return true
                        val maxX = (screenWidth - closeButtonSize).coerceAtLeast(0)
                        val maxY = (screenHeight - closeButtonSize).coerceAtLeast(0)
                        params.x = (dragStartParamsX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (dragStartParamsY + dy.toInt()).coerceIn(0, maxY)
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (_: IllegalArgumentException) {
                            // View not attached (dismissed mid-drag); ignore.
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // A tap (no movement beyond slop) — close the overlay.
                        onCloseRequested()
                    }
                    isDragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            // Consume every event so the overlay keeps receiving the gesture stream.
            return true
        }
    }
}
