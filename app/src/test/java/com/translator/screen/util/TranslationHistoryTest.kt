package com.translator.screen.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the on-device translation history (Phase 4). Covers the round-trip,
 * consecutive-duplicate suppression, the [TranslationHistory.MAX_ENTRIES] cap, blank
 * rejection, and clear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TranslationHistoryTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TranslationHistory.clear(context)
    }

    @Test
    fun empty_whenNothingAdded() {
        assertThat(TranslationHistory.all(context)).isEmpty()
    }

    @Test
    fun add_thenReadBack() {
        TranslationHistory.add(context, "Hallo", "Hello", "com.example.app")
        val entries = TranslationHistory.all(context)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].sourceText).isEqualTo("Hallo")
        assertThat(entries[0].translatedText).isEqualTo("Hello")
        assertThat(entries[0].appPackage).isEqualTo("com.example.app")
    }

    @Test
    fun add_dedupesConsecutiveIdentical() {
        TranslationHistory.add(context, "Hallo", "Hello", "com.example.app")
        TranslationHistory.add(context, "Hallo", "Hello", "com.example.app")
        assertThat(TranslationHistory.all(context)).hasSize(1)
    }

    @Test
    fun add_allowsDifferentEntryAfterDupe() {
        TranslationHistory.add(context, "Hallo", "Hello", "com.example.app")
        TranslationHistory.add(context, "Hallo", "Hello", "com.example.app")
        TranslationHistory.add(context, "Welt", "World", "com.example.app")
        val entries = TranslationHistory.all(context)
        assertThat(entries).hasSize(2)
        assertThat(entries[1].sourceText).isEqualTo("Welt")
    }

    @Test
    fun clear_removesEverything() {
        TranslationHistory.add(context, "Hallo", "Hello", "com.example.app")
        TranslationHistory.clear(context)
        assertThat(TranslationHistory.all(context)).isEmpty()
    }

    @Test
    fun add_capsAtMaxEntries() {
        for (i in 1..55) {
            TranslationHistory.add(context, "src$i", "tgt$i", "com.example.app")
        }
        val entries = TranslationHistory.all(context)
        assertThat(entries).hasSize(50)
        // Oldest entries (src1..src5) dropped; newest (src55) retained.
        assertThat(entries.last().sourceText).isEqualTo("src55")
        assertThat(entries.first().sourceText).isEqualTo("src6")
    }

    @Test
    fun add_ignoresBlankSourceOrTarget() {
        TranslationHistory.add(context, "", "Hello", "com.example.app")
        TranslationHistory.add(context, "Hallo", "", "com.example.app")
        assertThat(TranslationHistory.all(context)).isEmpty()
    }
}
