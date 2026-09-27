package uk.elizabeth.aac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.aac.core.data.WrongPassphraseException
import uk.elizabeth.aac.core.model.History
import uk.elizabeth.aac.data.Repository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exportEraseRestoreRoundTrip() = runBlocking {
        val repo = Repository(context)
        val data = AppData(history = History().add("Could I have a cup of tea?", 1, 10))
        repo.saveAppData(data)
        repo.saveWordModel("1\ttea\t3\n")
        repo.saveRecording("rec1", byteArrayOf(1, 2, 3))
        repo.saveVoiceTake("vb1", byteArrayOf(4, 5, 6))

        val out = ByteArrayOutputStream()
        repo.exportBackup(out, data, "1\ttea\t3\n", "correct horse".toCharArray())
        val export = out.toByteArray()

        repo.eraseAll()
        assertTrue(repo.load().isFirstRun)

        // A wrong passphrase changes nothing.
        try {
            repo.restoreBackup({ ByteArrayInputStream(export) }, "wrong horse!".toCharArray())
            fail("Wrong passphrase accepted")
        } catch (expected: WrongPassphraseException) {
        }
        assertTrue(repo.recordingIds().isEmpty())

        val restored = repo.restoreBackup({ ByteArrayInputStream(export) }, "correct horse".toCharArray())
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
        repo.exportBackup(out, AppData(), "", "correct horse".toCharArray())
        val truncated = out.toByteArray().let { it.copyOf(it.size - 40) }
        try {
            repo.restoreBackup({ ByteArrayInputStream(truncated) }, "correct horse".toCharArray())
            fail("Truncated export accepted")
        } catch (expected: Exception) {
        }
        assertArrayEquals(byteArrayOf(9), repo.loadRecording("keep"))
        repo.eraseAll()
    }
}
