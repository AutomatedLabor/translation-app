package com.translator.screen

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.translator.screen.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke test — the "prove it works" test.
 *
 * Launches [MainActivity] on the emulator and asserts it reaches the RESUMED state
 * without crashing. This exercises the full app startup path end-to-end:
 *  - [com.translator.screen.App] (Application.onCreate installs the crash logger via
 *    BuildConfigProxy + CrashLogger.install),
 *  - the manifest (declared activities, the widget/stop receivers, the splash theme),
 *  - the theme (Theme.ScreenTranslator + the values-v31 splash attrs; no-op on the
 *    API 30 CI emulator since v31/ is not selected below API 31),
 *  - enableEdgeToEdge(),
 *  - the Compose setup screen (ScreenTranslatorSetupScreen with its Status / Accessibility
 *    / Language / Model / Tile / History / Crash / Help cards + the 1/s live-status
 *    polling LaunchedEffect + the onboarding dialog on first launch).
 *
 * If any of those throw during creation, the scenario never reaches RESUMED and the test
 * fails. This is the "Batch 1: App launches, main screen renders" instrumented test —
 * stronger than a tautological `assertEquals(true, true)` because it actually launches
 * the real Activity on a real (emulated) device and asserts the lifecycle completes.
 *
 * Runs on the CI emulator (API 30, x86_64, google_apis, KVM, swiftshader) as part of
 * `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

    @Test
    fun appLaunches_andMainActivityReachesResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }
}
