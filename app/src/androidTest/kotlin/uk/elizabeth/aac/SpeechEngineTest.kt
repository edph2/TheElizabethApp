package uk.elizabeth.aac

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.elizabeth.aac.speech.EngineClient
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app and the separate Piper Voice Engine app working together, as on a customer's tablet:
 * managing voices through the engine's provider, and speaking through Android's TextToSpeech API.
 * Needs the engine's debug build installed (CI does this).
 */
@RunWith(AndroidJUnit4::class)
class SpeechEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = EngineClient(context)

    @After
    fun cleanUp() {
        if (engine.canManage()) runCatching { engine.deleteVoice(TestVoice.ID) }
    }

    @Test
    fun engineIsInstalledAndLetsTheAppManageVoices() {
        assumeTrue("speech engine not installed", engine.isInstalled())
        assertTrue("no permission to manage voices (are both apps signed with the same key?)", engine.canManage())
    }

    @Test
    fun installsListsAndSpeaksWithAVoiceThroughTheEngine() {
        assumeTrue(engine.isInstalled() && TestVoice.available())
        TestVoice.install(context, engine)
        val voice = engine.voices().single { it.id == TestVoice.ID }
        assertEquals("stock", voice.kind)
        assertEquals("en-GB", voice.locale)

        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts = TextToSpeech(context, { status = it; ready.countDown() }, EngineClient.PACKAGE)
        try {
            assertTrue(ready.await(30, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, status)
            tts.setVoice(tts.voices.first { it.name == TestVoice.ID })
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = done.countDown()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = done.countDown()
            })
            val file = File(context.cacheDir, "engine.wav")
            tts.synthesizeToFile("Hello. Could I have a cup of tea, please?", Bundle(), file, "u1")
            assertTrue(done.await(120, TimeUnit.SECONDS))
            // 16-bit mono at 22.05 kHz: over a second of speech is well over 44 KB.
            assertTrue("too little audio: ${file.length()} bytes", file.length() > 44_100)
            file.delete()
        } finally {
            tts.shutdown()
        }

        engine.deleteVoice(TestVoice.ID)
        assertTrue(engine.voices().none { it.id == TestVoice.ID })
    }
}
