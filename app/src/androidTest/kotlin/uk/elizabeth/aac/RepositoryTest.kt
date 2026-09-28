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
import uk.elizabeth.voiceformat.WrongPassphraseException
import uk.elizabeth.aac.core.model.History
import uk.elizabeth.aac.data.Repository
import uk.elizabeth.aac.speech.EngineClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val engine = EngineClient(context)

    @Test
    fun exportEraseRestoreRoundTrip() = runBlocking {
        val repo = Repository(context)
        val data = AppData(history = History().add("Could I have a cup of tea?", 1, 10))
        repo.saveAppData(data)
        repo.saveWordModel("1\ttea\t3\n")
        repo.saveRecording("rec1", byteArrayOf(1, 2, 3))
        repo.saveVoiceTake("vb1", byteArrayOf(4, 5, 6))

        val out = ByteArrayOutputStream()
        val info = repo.exportBackup(out, data, "1\ttea\t3\n", "correct horse".toCharArray(), emptyList(), engine::openVoice, 1234)
        assertEquals(BackupInfo(1234, 1, 1, 0), info)
        val export = out.toByteArray()

        repo.eraseAll()
        assertTrue(repo.load().isFirstRun)

        // Checking reads everything and changes nothing.
        assertEquals(info, repo.checkBackup(ByteArrayInputStream(export), "correct horse".toCharArray()))
        assertTrue(repo.load().isFirstRun)

        // A wrong passphrase changes nothing.
        try {
            repo.restoreBackup({ ByteArrayInputStream(export) }, "wrong horse!".toCharArray(), null)
            fail("Wrong passphrase accepted")
        } catch (expected: WrongPassphraseException) {
        }
        assertTrue(repo.recordingIds().isEmpty())

        val restored = repo.restoreBackup({ ByteArrayInputStream(export) }, "correct horse".toCharArray(), null)
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
        repo.exportBackup(out, AppData(), "", "correct horse".toCharArray(), emptyList(), engine::openVoice, 1)
        val truncated = out.toByteArray().let { it.copyOf(it.size - 40) }
        try {
            repo.restoreBackup({ ByteArrayInputStream(truncated) }, "correct horse".toCharArray(), null)
            fail("Truncated export accepted")
        } catch (expected: Exception) {
        }
        assertArrayEquals(byteArrayOf(9), repo.loadRecording("keep"))
        repo.eraseAll()
    }

    @Test
    fun installedVoicesAreBackedUpAndRestored() = runBlocking {
        assumeTrue("speech engine or test voice missing", engine.canManage() && TestVoice.available())
        val repo = Repository(context)
        // Written to a file, as the app does: a backup with a voice is too big to hold in memory.
        val backup = File(context.cacheDir, "voice-backup-test.elizbak")
        try {
            TestVoice.install(context, engine)
            val info = backup.outputStream().use { out ->
                repo.exportBackup(out, AppData(), "", "correct horse".toCharArray(), listOf(TestVoice.ID), engine::openVoice, 1)
            }
            assertEquals(1, info.voices)
            engine.deleteVoice(TestVoice.ID)
            assertTrue(engine.voices().none { it.id == TestVoice.ID })

            assertEquals(1, backup.inputStream().use { repo.checkBackup(it, "correct horse".toCharArray()) }.voices)
            val result = repo.restoreBackup({ backup.inputStream() }, "correct horse".toCharArray(), engine::installVoice)
            assertTrue(result.failedVoices.toString(), result.failedVoices.isEmpty())
            assertTrue(engine.voices().any { it.id == TestVoice.ID })

            // Without the engine, voices are reported rather than silently lost.
            val withoutEngine = repo.restoreBackup({ backup.inputStream() }, "correct horse".toCharArray(), null)
            assertEquals(listOf(TestVoice.ID), withoutEngine.failedVoices)
        } finally {
            backup.delete()
            runCatching { engine.deleteVoice(TestVoice.ID) }
            repo.eraseAll()
        }
    }
}
