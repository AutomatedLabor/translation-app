package com.translator.screen.util

import android.content.Context

/**
 * Persists the user's chosen target language (BCP-47 tag, e.g. "en", "ur") for the
 * German→target translation. Read by [TranslatorAccessibilityService] on connect and
 * by [MainActivity] for the test-translation engine. The service also exposes
 * [updateTargetLanguage] so a live change in the UI is picked up without re-enabling
 * the accessibility service.
 */
object LanguagePrefs {
    private const val PREFS = "translator_prefs"
    private const val TARGET_TAG = "target_language_tag"
    const val DEFAULT_TARGET_TAG = "en"
    private const val SOURCE_TAG = "source_language_tag"
    const val DEFAULT_SOURCE_TAG = "auto"

    fun getSourceTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SOURCE_TAG, DEFAULT_SOURCE_TAG) ?: DEFAULT_SOURCE_TAG

    fun setSourceTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SOURCE_TAG, tag).apply()
    }

    fun getTargetTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TARGET_TAG, DEFAULT_TARGET_TAG) ?: DEFAULT_TARGET_TAG

    fun setTargetTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TARGET_TAG, tag).apply()
    }
}
