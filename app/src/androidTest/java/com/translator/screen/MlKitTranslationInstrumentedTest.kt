package com.translator.screen

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.translator.screen.translate.MlKitTranslationEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test of the REAL ML Kit translation pipeline on the emulator — the
 * "does the translation functionality actually work?" test.
 *
 * Creates an [MlKitTranslationEngine] (de → en), downloads the offline model (one-time,
 * ~30 MB over the emulator's network), and translates "Hallo Welt". Asserts the result
 * is non-empty AND differs from the source — proving on-device ML Kit translation
 * actually produces output, not just that the app compiles/launches.
 *
 * This complements:
 *  - [ScreenTextExtractorInstrumentedTest] (proves German text is READ from a live
 *    accessibility tree via UiAutomation), and
 *  - [AppLaunchSmokeTest] (proves the app + MainActivity reach RESUMED).
 *
 * Together the three cover app-startup → text-extraction → text-translation on a real
 * (emulated) device. The live OVERLAY window (service → pickTargetWindow →
 * OverlayManager.show) is NOT CI-tested because pickTargetWindow deliberately excludes
 * the app's own package and the CI emulator has no foreign German app to translate —
 * that path needs a physical device with real German apps (DHL, banking, etc.).
 *
 * `withTimeout(120s)` guards against a hung ML Kit Task (network/model-server stall).
 */
@RunWith(AndroidJUnit4::class)
class MlKitTranslationInstrumentedTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun germanToEnglish_translatesOnDevice() {
        val engine = MlKitTranslationEngine("de", "en")
        try {
            runBlocking {
                withTimeout(120_000L) {
                    // Pre-download the de→en model so the translate call doesn't depend
                    // on the auto-retry-on-model-missing path (faster + deterministic).
                    engine.downloadModelIfNeeded()
                    val result = engine.translate("Hallo Welt")
                    // ML Kit returns the source unchanged if the pre-filter rejects it,
                    // so "result != source" proves a real translation happened.
                    assertThat(result).isNotEmpty()
                    assertThat(result).isNotEqualTo("Hallo Welt")
                }
            }
        } finally {
            engine.close()
        }
    }
}
