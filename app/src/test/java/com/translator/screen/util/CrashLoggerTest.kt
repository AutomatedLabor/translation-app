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
 * Unit tests for the local crash logger (Phase 5). Exercises [CrashLogger.writeCrash]
 * (internal — same package), [CrashLogger.crashFiles], and the [CrashLogger.MAX_LOGS]
 * pruning. Uses [CrashLogger.writeCrash] directly rather than installing the handler,
 * so no real crash is triggered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrashLoggerTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        CrashLogger.crashFiles(context).forEach { it.delete() }
    }

    @Test
    fun writeCrash_createsAFileWithTheStackTrace() {
        CrashLogger.writeCrash(context, Thread.currentThread(), RuntimeException("boom"))
        val files = CrashLogger.crashFiles(context)
        assertThat(files).isNotEmpty()
        val content = files.first().readText()
        assertThat(content).contains("Screen Translator crash report")
        assertThat(content).contains("boom")
        assertThat(content).contains("RuntimeException")
    }

    @Test
    fun crashFiles_returnsNewestFirst() {
        CrashLogger.writeCrash(context, Thread.currentThread(), RuntimeException("first"))
        Thread.sleep(3)  // distinct millisecond filename
        CrashLogger.writeCrash(context, Thread.currentThread(), RuntimeException("second"))
        val files = CrashLogger.crashFiles(context)
        assertThat(files).hasSize(2)
        assertThat(files.first().readText()).contains("second")
    }

    @Test
    fun writeCrash_prunesOldLogsBeyondMax() {
        // MAX_LOGS = 5. Write 7; only the 5 newest survive.
        for (i in 1..7) {
            CrashLogger.writeCrash(context, Thread.currentThread(), RuntimeException("crash$i"))
            Thread.sleep(3)  // distinct millisecond filename
        }
        val files = CrashLogger.crashFiles(context)
        assertThat(files).hasSize(5)
        // Oldest (crash1, crash2) pruned; newest surviving = crash7.
        assertThat(files.first().readText()).contains("crash7")
    }
}
