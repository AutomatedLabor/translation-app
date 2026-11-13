package com.translator.screen

import android.app.Application
import com.translator.screen.util.BuildConfigProxy
import com.translator.screen.util.CrashLogger

/**
 * Application entry point.
 *
 * Phase 5: installs a local crash logger ([CrashLogger]) as the process-wide
 * [Thread.UncaughtExceptionHandler]. Uncaught exceptions are persisted to
 * `filesDir/crashes/` (bounded, private, never uploaded) before the app terminates,
 * so bug reports can include a real stack trace instead of "it crashed".
 *
 * Also publishes the release [BuildConfig] version into [BuildConfigProxy] so the
 * crash logger (unit-tested without the generated BuildConfig on the classpath) can
 * record the app version in each report.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BuildConfigProxy.set(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        CrashLogger.install(this)
    }
}
