package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.audio.Chime
import uk.elizabeth.aac.core.data.Backup
import uk.elizabeth.aac.core.data.WrongPassphraseException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupTest {
    private val pass = "correct horse".toCharArray()
    private val bigRecording = Random(7).nextBytes(300_000) // spans several chunks

    private fun export(): ByteArray {
        val out = ByteArrayOutputStream()
        Backup.write(out, pass.copyOf()) { w ->
            w.put(Backup.APP_DATA, """{"version":1}""")
            w.put(Backup.WORDS, "1\ttea\t3\n")
            w.put(Backup.recordingEntry("abc-123"), bigRecording)
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray, passphrase: CharArray = pass.copyOf()): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        Backup.read(ByteArrayInputStream(bytes), passphrase) { name, data -> entries[name] = data }
        return entries
    }

    @Test
    fun `round trip across several chunks`() {
        val entries = read(export())
        assertEquals(listOf("appdata.json", "words.txt", "recordings/abc-123.wav"), entries.keys.toList())
        assertEquals("1\ttea\t3\n", entries.getValue("words.txt").decodeToString())
        assertContentEquals(bigRecording, entries.getValue("recordings/abc-123.wav"))
    }

    @Test
    fun `wrong passphrase is detected`() {
        assertFailsWith<WrongPassphraseException> { read(export(), "wrong horse!".toCharArray()) }
    }

    @Test
    fun `tampering is detected`() {
        val bytes = export()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        assertFailsWith<IOException> { read(bytes) }
    }

    @Test
    fun `truncation is detected`() {
        val bytes = export()
        assertFailsWith<IOException> { read(bytes.copyOf(bytes.size - 100)) }
        assertFailsWith<IOException> { read(bytes.copyOf(bytes.size - 30_000)) }
    }

    @Test
    fun `extra data after the end is detected`() {
        assertFailsWith<IOException> { read(export() + byteArrayOf(0, 0, 0)) }
    }

    @Test
    fun `other files are refused`() {
        assertFailsWith<IllegalArgumentException> { read("PK\u0003\u0004 not an export".toByteArray()) }
    }

    @Test
    fun `short passphrases and unsafe names are refused`() {
        assertFailsWith<IllegalArgumentException> { Backup.write(ByteArrayOutputStream(), "short".toCharArray()) {} }
        assertFailsWith<IllegalArgumentException> {
            Backup.write(ByteArrayOutputStream(), pass.copyOf()) { it.put("../evil", "x") }
        }
    }

    @Test
    fun `chime is audible and not clipped`() {
        val chime = Chime.generate()
        val peak = chime.samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 15_000..32_000)
        assertTrue(chime.durationMs in 1390..1410)
    }
}
