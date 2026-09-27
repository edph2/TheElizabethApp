package uk.elizabeth.aac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.elizabeth.aac.speech.PiperVoice
import uk.elizabeth.aac.speech.VoiceModels
import kotlin.math.sqrt

/**
 * End to end: imports a real Piper voice package (made in CI by package_voice.py from a
 * published sherpa-onnx voice) and synthesises speech on the device with sherpa-onnx.
 * Skipped when the test voice is not present (e.g. local runs without it).
 */
@RunWith(AndroidJUnit4::class)
class VoiceEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val models = VoiceModels(instrumentation.targetContext)

    @After
    fun cleanUp() = models.deleteAll()

    @Test
    fun importsAPackagedVoiceAndSpeaksOnDevice() {
        val assets = instrumentation.context.assets
        assumeTrue("test voice not packaged", assets.list("")?.contains(TEST_VOICE) == true)

        val voice = assets.open(TEST_VOICE).use { models.import(it, PASSPHRASE.toCharArray()) }
        assertEquals("stock", voice.manifest.kind)
        assertEquals(listOf(voice.id), models.list().map { it.id })

        val piper = PiperVoice(voice)
        try {
            val audio = piper.synthesize("Hello. Could I have a cup of tea, please?", 1.0f)
            assertEquals(22050, audio.sampleRate)
            assertTrue("too short: ${audio.durationMs} ms", audio.durationMs > 1_000)
            val rms = sqrt(audio.samples.sumOf { it.toDouble() * it } / audio.samples.size)
            assertTrue("silent output (rms $rms)", rms > 300)
        } finally {
            piper.release()
        }
    }

    @Test
    fun wrongPassphraseInstallsNothing() {
        val assets = instrumentation.context.assets
        assumeTrue("test voice not packaged", assets.list("")?.contains(TEST_VOICE) == true)
        val result = runCatching { assets.open(TEST_VOICE).use { models.import(it, "wrong horse!".toCharArray()) } }
        assertTrue(result.isFailure)
        assertTrue(models.list().isEmpty())
    }

    private companion object {
        const val TEST_VOICE = "test.elizvoice"
        const val PASSPHRASE = "correct horse battery"
    }
}
