package com.translator.screen.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device translation history (Phase 4). A bounded ring buffer of recent
 * translations, persisted as JSON to `filesDir/translation_history.json`.
 *
 * Why not Room? Room would add the KSP annotation-processor plugin + a runtime
 * dependency, and a version mismatch with Kotlin 2.0.21 risks a CI break (the
 * v2.0.4 splash failed for exactly this reason — a new artifact did not resolve).
 * The history is tiny (≤ [MAX_ENTRIES] rows) and append-mostly, so a plain JSON
 * file rewritten on each add is simpler, dependency-free, and fast enough.
 *
 * Privacy: stored only in the app's private internal storage (`filesDir`), covered
 * by `allowBackup=false` + `dataExtractionRules` excluding every domain. The user
 * can clear it at any time via [clear]. Nothing is uploaded.
 *
 * Thread-safe: every public method synchronizes on [lock], so concurrent access
 * from the accessibility service (background coroutine) and the UI (main thread)
 * is safe. File I/O is minimal (≤ [MAX_ENTRIES] rows of short text).
 */
object TranslationHistory {

    private const val TAG = "TranslationHistory"
    private const val FILE_NAME = "translation_history.json"
    private const val MAX_ENTRIES = 50

    data class Entry(
        val sourceText: String,
        val translatedText: String,
        val appPackage: String,
        val timestampMs: Long,
    )

    private val lock = Any()

    /**
     * Append a translation. Trims source/translated text to [MAX_TEXT_LEN] so a single
     * huge region cannot bloat the file. Consecutive duplicates (same source+target)
     * are dropped to avoid filling the buffer with the same translated chat message.
     */
    fun add(context: Context, source: String, translated: String, appPackage: String) {
        if (source.isBlank() || translated.isBlank()) return
        synchronized(lock) {
            val entries = readInternal(context).toMutableList()
            val last = entries.lastOrNull()
            val newEntry = Entry(
                sourceText = source.take(MAX_TEXT_LEN),
                translatedText = translated.take(MAX_TEXT_LEN),
                appPackage = appPackage,
                timestampMs = System.currentTimeMillis(),
            )
            if (last != null &&
                last.sourceText == newEntry.sourceText &&
                last.translatedText == newEntry.translatedText
            ) {
                // Same as the most recent entry — skip to avoid dupes from live mode.
                return
            }
            entries.add(newEntry)
            while (entries.size > MAX_ENTRIES) entries.removeAt(0)
            writeInternal(context, entries)
        }
    }

    /** Return all stored entries, newest last. Empty if none or on read error. */
    fun all(context: Context): List<Entry> = synchronized(lock) { readInternal(context) }

    /** Delete the history file. No-op if absent. */
    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { historyFile(context).delete() }
        }
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private const val MAX_TEXT_LEN = 500

    private fun historyFile(context: Context): File =
        File(context.filesDir, FILE_NAME)

    private fun readInternal(context: Context): List<Entry> {
        val file = historyFile(context)
        if (!file.isFile) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Entry(
                            sourceText = o.optString("source", ""),
                            translatedText = o.optString("target", ""),
                            appPackage = o.optString("pkg", ""),
                            timestampMs = o.optLong("ts", 0L),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read history: ${e.message}; resetting")
            runCatching { file.delete() }
            emptyList()
        }
    }

    private fun writeInternal(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("source", e.sourceText)
                    put("target", e.translatedText)
                    put("pkg", e.appPackage)
                    put("ts", e.timestampMs)
                },
            )
        }
        try {
            historyFile(context).writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write history: ${e.message}")
        }
    }
}
