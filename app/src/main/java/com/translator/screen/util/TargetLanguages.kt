package com.translator.screen.util

/**
 * The curated set of target languages offered in the UI for German→target translation.
 * Each entry pairs a BCP-47 tag (passed to [com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag])
 * with a human-readable label (including the language's own endonym where useful).
 *
 * Source language is fixed to German per the app's spec (a German-resident / visitor
 * reading German apps). Adding more entries here automatically extends the picker.
 */
object TargetLanguages {
    data class Entry(val tag: String, val display: String)

    val ALL: List<Entry> = listOf(
        Entry("en", "English"),
        Entry("ur", "Urdu (اردو)"),
        Entry("fr", "French"),
        Entry("es", "Spanish"),
        Entry("it", "Italian"),
        Entry("pt", "Portuguese"),
        Entry("ru", "Russian"),
        Entry("ar", "Arabic (العربية)"),
        Entry("tr", "Turkish"),
        Entry("hi", "Hindi (हिन्दी)"),
        Entry("zh", "Chinese (中文)"),
        Entry("ja", "Japanese (日本語)"),
    )

    fun displayFor(tag: String): String =
        if (tag == "auto") "Auto-Detect" else ALL.firstOrNull { it.tag == tag }?.display ?: tag

    /** Source languages: "Auto-Detect" first, then the same list as target. */
    val ALL_SOURCE: List<Entry> = listOf(Entry("auto", "Auto-Detect")) + ALL
}
