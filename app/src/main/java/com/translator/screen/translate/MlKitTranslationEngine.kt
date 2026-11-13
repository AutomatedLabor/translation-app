package com.translator.screen.translate

import android.util.Log
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ML Kit on-device translator (German → targetLanguageTag).
 *
 * - Lazily creates the [Translator] and [LanguageIdentifier] under [initLock] with
 *   double-checked locking.
 * - Pre-filters structural noise (pure numbers, dates, times, tracking codes, URLs,
 *   emails, pure punctuation) and confidently-English text via [shouldTranslate] so
 *   the ML model is only invoked for genuine German prose.
 * - Caches the most recent 500 translations in an [LruCache] keyed on the trimmed,
 *   lowercased source.
 * - Auto-retries a translation once if ML Kit reports the model is missing, by
 *   triggering [downloadModelIfNeeded].
 *
 * All suspending entry points are main-safe: ML work runs on [Dispatchers.Default].
 */
class MlKitTranslationEngine(
  override val sourceLanguageTag: String = "auto",
  override val targetLanguageTag: String = "en",
) : ITranslationEngine {

  private var translator: Translator? = null
  private var languageIdentifier: LanguageIdentifier? = null
  private val cache = LruCache<String, String>(500)
  private val closed = AtomicBoolean(false)
  private val initLock = Any()
  private var cachedDetectedSource: String? = null

  // -------------------------------------------------------------------------
  // ITranslationEngine
  // -------------------------------------------------------------------------

  override fun warmup() {
    if (closed.get()) return
    // Fire-and-forget trivial translation to spin up the native model.
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.Default) {
      // Re-check closed inside the coroutine: close() may have run between warmup() being
      // scheduled and this block executing. Without this, getOrCreateTranslator() would
      // (re)create a Translator after close() nulled it, leaking the native model.
      // (Fresh-eyes review P1-8.)
      if (closed.get()) return@launch
      runCatching {
        getOrCreateTranslator(defaultSourceLanguage())?.translate("hallo")?.await()
      }.onFailure { e ->
        Log.d(TAG, "Warmup failed: ${e.message}")
      }
    }
  }

  override suspend fun shouldTranslate(source: String): Boolean =
    withContext(Dispatchers.Default) {
      if (closed.get()) {
        Log.d(TAG, "Skipped by filter: engine closed")
        return@withContext false
      }
      if (source.isBlank() || source.length < 2) {
        Log.d(TAG, "Skipped by filter: blank or too short")
        return@withContext false
      }
      if (!source.any { it.isLetter() }) {
        Log.d(TAG, "Skipped by filter: no alphabetic chars")
        return@withContext false
      }
      val trimmed = source.trim()
      if (NUMBER_REGEX.matches(trimmed)) {
        Log.d(TAG, "Skipped by filter: pure number/decimal")
        return@withContext false
      }
      if (PUNCT_REGEX.matches(trimmed)) {
        Log.d(TAG, "Skipped by filter: pure punctuation")
        return@withContext false
      }
      if (DATE_REGEX.matches(trimmed)) {
        Log.d(TAG, "Skipped by filter: date-like")
        return@withContext false
      }
      if (TIME_REGEX.matches(trimmed)) {
        Log.d(TAG, "Skipped by filter: time-like")
        return@withContext false
      }
      if (TRACKING_REGEX.matches(trimmed)) {
        Log.d(TAG, "Skipped by filter: tracking code")
        return@withContext false
      }
      if (URL_EMAIL_REGEX.matches(trimmed)) {
        Log.d(TAG, "Skipped by filter: URL or email")
        return@withContext false
      }

      val lang: String
      val conf: Float
      try {
        val identifier = getOrCreateLanguageIdentifier()
        val langs = identifier.identifyPossibleLanguages(trimmed).await()
        val best = langs.maxByOrNull { it.confidence }
        lang = best?.languageTag?.substringBefore('-')?.lowercase() ?: "und"
        conf = best?.confidence ?: 0f
        Log.d(TAG, "Language-id result: lang=$lang conf=$conf (text length=${trimmed.length})")
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.d(TAG, "Language-id failure: ${e.message}; defaulting to translate=true")
        return@withContext true
      }

      val targetLangCode = targetLanguageTag.lowercase()
      when {
        lang == targetLangCode && conf > 0.75f -> {
          Log.d(TAG, "Skipped by filter: confidently target language ($lang)")
          false
        }
        sourceLanguageTag != "auto" -> {
          val sourceLangCode = sourceLanguageTag.lowercase()
          if (lang == sourceLangCode || lang == "und") {
            true
          } else if (conf > 0.75f) {
            Log.d(TAG, "Skipped by filter: non-source language=$lang conf=$conf")
            false
          } else {
            true
          }
        }
        else -> true // auto-detect: pass everything except the target language
      }
    }

  override suspend fun translate(source: String): String =
    withContext(Dispatchers.Default) {
      if (closed.get()) return@withContext source

      if (!shouldTranslate(source)) {
        Log.d(TAG, "Skipped by filter (length=${source.length})")
        return@withContext source
      }

      val key = source.trim().lowercase()
      val cached: String? = synchronized(cache) { cache.get(key) }
      if (cached != null) {
        Log.d(TAG, "Cache hit (key length=${key.length})")
        return@withContext cached
      }
      Log.d(TAG, "Cache miss (key length=${key.length})")

      var retriedAfterDownload = false
      var attempt = 0
      while (attempt < 2) {
        attempt++
        try {
          val sourceLang = resolveSourceLanguage(source)
          val t = getOrCreateTranslator(sourceLang) ?: return@withContext source
          val result = t.translate(source).await()
          synchronized(cache) { cache.put(key, result) }
          Log.d(TAG, "Translated (source len=${source.length}, result len=${result.length})")
          return@withContext result
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          Log.d(TAG, "Translate failure: ${e.message}")
          if (!retriedAfterDownload && isModelMissingError(e)) {
            retriedAfterDownload = true
            Log.d(TAG, "Model missing; attempting download then retry once")
            try {
              downloadModelIfNeeded()
            } catch (dlEx: Exception) {
              if (dlEx is CancellationException) throw dlEx
              Log.d(TAG, "Retry download failed: ${dlEx.message}")
              return@withContext source
            }
            // fall through to next loop iteration for the single retry
          } else {
            return@withContext source
          }
        }
      }
      return@withContext source
    }

  override suspend fun isModelDownloaded(): Boolean =
    withContext(Dispatchers.Default) {
      try {
        val mgr = RemoteModelManager.getInstance()
        val sourceLang = cachedDetectedSource ?: TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: TranslateLanguage.GERMAN
        val sourceModel = TranslateRemoteModel.Builder(sourceLang).build()
        val targetModel = TranslateRemoteModel.Builder(targetTranslateLanguage()).build()
        val sourceReady = mgr.isModelDownloaded(sourceModel).await()
        val targetReady = mgr.isModelDownloaded(targetModel).await()
        Log.d(TAG, "isModelDownloaded: source=$sourceReady target=$targetReady ($sourceLanguageTag -> $targetLanguageTag)")
        sourceReady && targetReady
      } catch (e: Exception) {
        Log.d(TAG, "isModelDownloaded failure: ${e.message}")
        false
      }
    }

  override suspend fun downloadModelIfNeeded() =
    withContext(Dispatchers.Default) {
      if (closed.get()) return@withContext
      if (isModelDownloaded()) {
        Log.d(TAG, "Model already downloaded; skipping")
        return@withContext
      }
      Log.d(TAG, "Model download start")
      try {
        val t = getOrCreateTranslator(defaultSourceLanguage()) ?: return@withContext
        t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        Log.d(TAG, "Model download complete")
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.d(TAG, "Model download failed: ${e.message}")
        throw e
      }
    }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    Log.d(TAG, "close()")
    synchronized(initLock) {
      translator?.close()
      translator = null
      languageIdentifier?.close()
      languageIdentifier = null
      cachedDetectedSource = null
    }
    synchronized(cache) {
      cache.evictAll()
    }
  }

  // -------------------------------------------------------------------------
  // Internals
  // -------------------------------------------------------------------------

  private fun targetTranslateLanguage(): String =
    TranslateLanguage.fromLanguageTag(targetLanguageTag) ?: TranslateLanguage.ENGLISH

  /** The default source for warmup (no text available to detect from). */
  private fun defaultSourceLanguage(): String =
    if (sourceLanguageTag == "auto") TranslateLanguage.GERMAN
    else TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: TranslateLanguage.GERMAN

  /** Resolve the source language for [text]. If "auto", detect via LanguageIdentification + cache. */
  private suspend fun resolveSourceLanguage(text: String): String {
    if (sourceLanguageTag != "auto") {
      return TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: TranslateLanguage.GERMAN
    }
    cachedDetectedSource?.let { return it }
    return withContext(Dispatchers.Default) {
      try {
        val identifier = getOrCreateLanguageIdentifier()
        val langs = identifier.identifyPossibleLanguages(text).await()
        val best = langs.maxByOrNull { it.confidence }
        val tag = best?.languageTag?.substringBefore('-')?.lowercase() ?: "de"
        val resolved = TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.GERMAN
        cachedDetectedSource = resolved
        Log.d(TAG, "Auto-detected source: $resolved (conf=${best?.confidence}, tag=$tag)")
        resolved
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.d(TAG, "Auto-detect failed: ${e.message}; defaulting to GERMAN")
        TranslateLanguage.GERMAN
      }
    }
  }

  private fun getOrCreateTranslator(sourceLang: String): Translator? {
    // P1-8: never (re)create after close().
    if (closed.get()) return null
    translator?.let { return it }
    synchronized(initLock) {
      if (closed.get()) return null
      translator?.let { return it }
      val options = TranslatorOptions.Builder()
        .setSourceLanguage(sourceLang)
        .setTargetLanguage(targetTranslateLanguage())
        .build()
      val t = Translation.getClient(options)
      translator = t
      return t
    }
  }

  private fun getOrCreateLanguageIdentifier(): LanguageIdentifier {
    languageIdentifier?.let { return it }
    synchronized(initLock) {
      languageIdentifier?.let { return it }
      val id = LanguageIdentification.getClient()
      languageIdentifier = id
      return id
    }
  }

  private fun isModelMissingError(e: Throwable): Boolean {
    var cur: Throwable? = e
    while (cur != null) {
      val msg = cur.message?.lowercase().orEmpty()
      if (msg.contains("not downloaded") ||
          msg.contains("not yet downloaded") ||
          msg.contains("missing") ||
          msg.contains("model not found") ||
          msg.contains("required model") ||
          msg.contains("download required") ||
          msg.contains("download needed") ||
          (("model" in msg || "translate" in msg) &&
            ("not" in msg || "missing" in msg || "download" in msg))
      ) {
        return true
      }
      cur = cur.cause
    }
    return false
  }

  companion object {
    private const val TAG = "MlKitTranslationEngine"

    // Pure number / decimal, e.g. "42", "3,14", "1.234".
    private val NUMBER_REGEX = Regex("^[0-9]+([.,][0-9]+)?\$")

    // Pure punctuation / symbols (no letters or digits).
    private val PUNCT_REGEX = Regex("^[^\\p{L}\\p{N}]+\$")

    // ISO date-like, e.g. "2024-12-31", "31.12.2024", "12/31/24".
    private val DATE_REGEX = Regex("^\\d{1,4}[-/.\\s]\\d{1,2}[-/.\\s]\\d{1,4}\$")

    // Time-like, e.g. "12:34", "12:34:56".
    private val TIME_REGEX = Regex("^\\d{1,2}:\\d{2}([:\\d]*)?\$")

    // Tracking / parcel codes, e.g. "RR123456789DE", "JD001234567890123".
    private val TRACKING_REGEX =
      Regex("^\\s*[A-Z]{2,4}\\d{8,20}[A-Z]?\\s*\$", RegexOption.IGNORE_CASE)

    // URLs and emails (with or without scheme), plus bare domains like "example.de".
    private val URL_EMAIL_REGEX = Regex(
      "^(https?://|www\\.)\\S+\$" +
        "|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$" +
        "|^[A-Za-z0-9-]+\\.[A-Za-z]{2,}(/\\S*)?\$",
      RegexOption.IGNORE_CASE
    )
  }
}
