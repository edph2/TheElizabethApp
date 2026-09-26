package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.audio.Chime
import uk.elizabeth.aac.core.data.Backup
import uk.elizabeth.aac.core.data.BackupContents
import uk.elizabeth.aac.core.data.WrongPassphraseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupTest {
    private val contents = BackupContents(
        appDataJson = """{"version":1}""",
        wordModel = "1\ttea\t3\n",
        recordings = mapOf("abc-123" to byteArrayOf(1, 2, 3)),
    )

    @Test
    fun `zip round trip`() {
        val back = Backup.unzip(Backup.zip(contents))
        assertEquals(contents.appDataJson, back.appDataJson)
        assertEquals(contents.wordModel, back.wordModel)
        assertContentEquals(byteArrayOf(1, 2, 3), back.recordings["abc-123"])
    }

    @Test
    fun `encryption round trip and wrong passphrase`() {
        val plain = Backup.zip(contents)
        val sealed = Backup.encrypt(plain, "correct horse".toCharArray())
        assertContentEquals(plain, Backup.decrypt(sealed, "correct horse".toCharArray()))
        assertFailsWith<WrongPassphraseException> { Backup.decrypt(sealed, "wrong horse!".toCharArray()) }
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertFailsWith<WrongPassphraseException> { Backup.decrypt(sealed, "correct horse".toCharArray()) }
    }

    @Test
    fun `short passphrases are refused`() {
        assertFailsWith<IllegalArgumentException> { Backup.encrypt(byteArrayOf(), "short".toCharArray()) }
    }

    @Test
    fun `chime is audible and not clipped`() {
        val chime = Chime.generate()
        val peak = chime.samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 15_000..32_000)
        assertTrue(chime.durationMs in 1390..1410)
    }
}
