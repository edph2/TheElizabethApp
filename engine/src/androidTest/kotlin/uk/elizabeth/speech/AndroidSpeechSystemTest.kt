// SPDX-License-Identifier: GPL-3.0-or-later
// Piper Voice Engine. Free software under the GNU GPL v3 or later: see engine/LICENSE.
package uk.elizabeth.speech

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The engine used the way other apps use it: through Android's standard TextToSpeech API. Checks
 * that the voice sounds the same as synthesising directly, i.e. that going through Android's
 * speech system does not degrade it.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSpeechSystemTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val models = VoiceModels(context)

    @Before
    fun startClean() {
        models.deleteAll()
        VoiceChanges.changed()
    }

    @After
    fun cleanUp() {
        models.deleteAll()
        VoiceChanges.changed()
    }

    @Test
    fun audioThroughAndroidMatchesDirectSynthesis() {
        val assets = instrumentation.context.assets
        assumeTrue("test voice not packaged", assets.list("")?.contains("test.elizvoice") == true)
        val voice = assets.open("test.elizvoice").use { models.import(it, "correct horse battery".toCharArray()) }
        VoiceChanges.changed()
        val text = "Could I have a cup of tea, please?"

        val piper = PiperVoice(voice)
        val direct = piper.synthesize(text, 1.0f)
        val direct2 = piper.synthesize(text, 1.0f)
        piper.release()

        val viaAndroid = synthesiseThroughAndroid(voice.id, text)

        if (direct.contentEquals(direct2)) {
            // The model is deterministic, so Android's speech system must pass the audio through unchanged.
            assertArrayEquals(direct, viaAndroid)
        } else {
            // Piper adds natural random variation to each utterance, so compare length and loudness instead.
            val lengthRatio = viaAndroid.size.toDouble() / direct.size
            assertTrue("length differs: ${viaAndroid.size} vs ${direct.size}", abs(1 - lengthRatio) < 0.15)
            val loudnessRatio = rms(viaAndroid) / rms(direct)
            assertTrue("loudness differs: ratio $loudnessRatio", loudnessRatio in 0.75..1.33)
        }
    }

    private fun synthesiseThroughAndroid(voiceName: String, text: String): ShortArray {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts = TextToSpeech(context, { status = it; ready.countDown() }, context.packageName)
        try {
            assertTrue(ready.await(30, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, status)
            val voice = tts.voices.first { it.name == voiceName }
            assertEquals(false, voice.isNetworkConnectionRequired)
            tts.setVoice(voice)
            tts.setSpeechRate(1.0f)
            val done = CountDownLatch(1)
            var failed = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = done.countDown()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    failed = true
                    done.countDown()
                }
            })
            val file = File(context.cacheDir, "through-android.wav")
            assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile(text, Bundle(), file, "u1"))
            assertTrue("synthesis timed out", done.await(120, TimeUnit.SECONDS))
            assertTrue("synthesis failed", !failed)
            return readPcm(file.readBytes()).also { file.delete() }
        } finally {
            tts.shutdown()
        }
    }

    /** The 16-bit samples from the "data" chunk of a WAV file. */
    private fun readPcm(wav: ByteArray): ShortArray {
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4)
            val size = b.getInt(pos + 4).let { if (it < 0 || pos + 8 + it > wav.size) wav.size - pos - 8 else it }
            if (id == "data") return ShortArray(size / 2) { b.getShort(pos + 8 + it * 2) }
            pos += 8 + size + (size and 1)
        }
        error("No audio in WAV")
    }

    private fun rms(s: ShortArray) = sqrt(s.sumOf { it.toDouble() * it } / s.size)
}
