package com.translator.screen.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local crash logger (Phase 5). Installs a [Thread.UncaughtExceptionHandler] that
 * persists the stack trace of any uncaught exception to
 * `filesDir/crashes/crash-<timestamp>.txt` before delegating to the previous handler,
 * so the app still terminates normally (and the user sees the system crash dialog).
 *
 * Design notes:
 *  - **No telemetry, no network.** Crashes are written only to the app's private
 *    internal storage (`filesDir`), which is covered by `allowBackup=false` +
 *    `dataExtractionRules` excluding every domain. The user must explicitly share a
 *    crash log file (e.g. via a bug report) — nothing is uploaded automatically.
 *  - **Defensive.** Every I/O step is wrapped in try/catch: the handler itself must
 *    never throw (it runs in an already-failing process), otherwise it masks the
 *    original crash.
 *  - **Bounded.** Only the most recent [MAX_LOGS] crash files are retained; older
 *    ones are pruned on each invocation so the directory cannot grow unbounded.
 *  - **No dependency.** Pure JDK + Android API; no Firebase / Bugsnag / etc. (the
 *    app is offline-first and dependency-light by design).
 *
 * Installed from [com.translator.screen.App.onCreate].
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val CRASH_DIR = "crashes"
    private const val MAX_LOGS = 5

    /**
     * Install the handler. Captures the current (default or previously-installed)
     * handler so it can delegate after persisting. Idempotent: a process-level flag
     * guards against double-installation if [install] is called more than once.
     */
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (t: Throwable) {
                // Never let the logger mask the original crash.
                Log.e(TAG, "Failed to persist crash log", t)
            }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
        Log.i(TAG, "Local crash logger installed (writes to filesDir/$CRASH_DIR)")
    }

    /**
     * Persist a crash report + prune old logs. Visible for testing.
     */
    internal fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
        val file = File(dir, "crash-$ts.txt")
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("Screen Translator crash report")
            pw.println("Time: ${Date()}")
            pw.println("Thread: ${thread.name}")
            pw.println("App version: ${BuildConfigProxy.versionName} (code ${BuildConfigProxy.versionCode})")
            pw.println("Build: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT}, ${Build.VERSION.RELEASE})")
            pw.println()
            pw.println("Stack trace:")
            throwable.printStackTrace(pw)
        }
        file.writeText(sw.toString())
        // Prune AFTER writing so the count never exceeds [MAX_LOGS] (pruning before the
        // write would allow MAX_LOGS+1 files to linger until the next crash).
        pruneOldLogs(dir)
        Log.i(TAG, "Crash log written: ${file.name}")
    }

    /** List the crash log files (newest first), or empty if none. */
    fun crashFiles(context: Context): List<File> {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun pruneOldLogs(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size > MAX_LOGS) {
            files.drop(MAX_LOGS).forEach { f ->
                runCatching { f.delete() }
            }
        }
    }
}

/**
 * Tiny indirection over [com.translator.screen.BuildConfig] so [CrashLogger] (which is
 * in the `util` package and unit-tested in isolation) does not pull the generated
 * BuildConfig class into plain JVM tests where it is absent. The app's [App] supplies
 * the real values at install time via [setBuildConfigProxy].
 */
internal object BuildConfigProxy {
    var versionName: String = "unknown"
        private set
    var versionCode: Int = 0
        private set

    fun set(versionName: String, versionCode: Int) {
        this.versionName = versionName
        this.versionCode = versionCode
    }
}
