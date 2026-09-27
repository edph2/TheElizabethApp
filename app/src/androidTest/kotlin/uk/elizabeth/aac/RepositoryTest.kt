package uk.elizabeth.aac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.aac.core.data.BackupInfo
import uk.elizabeth.aac.core.data.WrongPassphraseException
import uk.elizabeth.aac.core.model.History
import uk.elizabeth.aac.data.Repository
import uk.elizabeth.aac.speech.PiperVoice
import uk.elizabeth.aac.speech.VoiceModels
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val voices = VoiceModels(context)

    @Test
    fun exportEraseRestoreRoundTrip() = runBlocking {
        val repo = Repository(context)
        val data = AppData(history = History().add("Could I have a cup of tea?", 1, 10))
        repo.saveAppData(data)
        repo.saveWordModel("1\ttea\t3\n")
        repo.saveRecording("rec1", byteArrayOf(1, 2, 3))
        repo.saveVoiceTake("vb1", byteArrayOf(4, 5, 6))

        val out = ByteArrayOutputStream()
        val info = repo.exportBackup(out, data, "1\ttea\t3\n", "correct horse".toCharArray(), voices, emptyList(), 1234)
        assertEquals(BackupInfo(1234, 1, 1, 0), info)
        val export = out.toByteArray()

        repo.eraseAll()
        assertTrue(repo.load().isFirstRun)

        // Checking reads everything and changes nothing.
        assertEquals(info, repo.checkBackup(ByteArrayInputStream(export), "correct horse".toCharArray()))
        assertTrue(repo.load().isFirstRun)

        // A wrong passphrase changes nothing.
        try {
            repo.restoreBackup({ ByteArrayInputStream(export) }, "wrong horse!".toCharArray(), voices)
            fail("Wrong passphrase accepted")
        } catch (expected: WrongPassphraseException) {
        }
        assertTrue(repo.recordingIds().isEmpty())

        val restored = repo.restoreBackup({ ByteArrayInputStream(export) }, "correct horse".toCharArray(), voices)
        assertEquals(data, restored.data)
        assertEquals("1\ttea\t3\n", restored.wordModel)
        assertArrayEquals(byteArrayOf(1, 2, 3), repo.loadRecording("rec1"))
        assertArrayEquals(byteArrayOf(4, 5, 6), repo.loadVoiceTake("vb1"))
        assertEquals(data, repo.load().data)
        repo.eraseAll()
    }

    @Test
    fun truncatedExportChangesNothing() = runBlocking {
        val repo = Repository(context)
        repo.saveRecording("keep", byteArrayOf(9))
        val out = ByteArrayOutputStream()
        repo.exportBackup(out, AppData(), "", "correct horse".toCharArray(), voices, emptyList(), 1)
        val truncated = out.toByteArray().let { it.copyOf(it.size - 40) }
        try {
            repo.restoreBackup({ ByteArrayInputStream(truncated) }, "correct horse".toCharArray(), voices)
            fail("Truncated export accepted")
        } catch (expected: Exception) {
        }
        assertArrayEquals(byteArrayOf(9), repo.loadRecording("keep"))
        repo.eraseAll()
    }

    @Test
    fun installedVoicesAreBackedUpAndRestored() = runBlocking {
        val assets = instrumentation.context.assets
        assumeTrue("test voice not packaged", assets.list("")?.contains("test.elizvoice") == true)
        val repo = Repository(context)
        // Written to a file, as the app does: a backup with a voice is too big to hold in memory.
        val backup = File(context.cacheDir, "voice-backup-test.elizbak")
        voices.deleteAll()
        try {
            val voice = assets.open("test.elizvoice").use { voices.import(it, "correct horse battery".toCharArray()) }

            val info = backup.outputStream().use { out ->
                repo.exportBackup(out, AppData(), "", "correct horse".toCharArray(), voices, voices.list(), 1)
            }
            assertEquals(1, info.voices)
            voices.deleteAll()
            assertTrue(voices.list().isEmpty())

            assertEquals(1, backup.inputStream().use { repo.checkBackup(it, "correct horse".toCharArray()) }.voices)
            val result = repo.restoreBackup({ backup.inputStream() }, "correct horse".toCharArray(), voices)
            assertTrue(result.failedVoices.isEmpty())
            val restored = voices.list().single()
            assertEquals(voice.id, restored.id)
            assertEquals(voice.manifest.modelSha256, restored.manifest.modelSha256)

            // The restored voice still speaks.
            val piper = PiperVoice(restored)
            try {
                assertTrue(piper.synthesize("Hello again.", 1.0f).durationMs > 300)
            } finally {
                piper.release()
            }
        } finally {
            backup.delete()
            voices.deleteAll()
            repo.eraseAll()
        }
    }
}
