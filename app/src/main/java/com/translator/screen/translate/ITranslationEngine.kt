package com.translator.screen.translate

/** Contract for the German→target translator. All methods are main-safe. */
interface ITranslationEngine {
  /** The BCP-47 source language tag ("auto" for auto-detect, or a specific tag like "de"). */
  val sourceLanguageTag: String

  /** The BCP-47 target language tag (e.g. "en", "ur"). */
  val targetLanguageTag: String

  /** Translate a single source string into [targetLanguageTag]. Returns source unchanged if filtered out. */
  suspend fun translate(source: String): String

  /** True if the German→[targetLanguageTag] offline model pair is downloaded and ready. */
  suspend fun isModelDownloaded(): Boolean

  /** Trigger ML Kit model download (idempotent). Suspends until download completes or fails. */
  suspend fun downloadModelIfNeeded()

  /** True if [source] passes the pre-translation filter (alphabetic, not pure number/date/tracking code, not confidently English). */
  suspend fun shouldTranslate(source: String): Boolean

  /** Release native resources. Safe to call multiple times. */
  fun close()

  /** Optional warmup; called from service onServiceConnected(). */
  fun warmup()
}
