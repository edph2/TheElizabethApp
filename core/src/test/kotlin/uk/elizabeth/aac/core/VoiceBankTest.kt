package uk.elizabeth.aac.core

import uk.elizabeth.voiceformat.Backup
import uk.elizabeth.aac.core.voicebank.TrainingExport
import uk.elizabeth.aac.core.voicebank.VoiceBank
import uk.elizabeth.voiceformat.VoiceConsent
import uk.elizabeth.aac.core.voicebank.VoiceTake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceBankTest {
    private fun take(i: Int, ms: Long = 3000) = VoiceTake(i, "vb-$i", ms, true, 0)

    @Test
    fun `script loads with about 300 unique sentences`() {
        val script = VoiceBank.loadScript()
        assertTrue(script.size >= 300, "only ${script.size}")
        assertEquals(script.size, script.toSet().size, "duplicate sentences")
    }

    @Test
    fun `takes replace earlier takes and progress is tracked`() {
        var bank = VoiceBank().withTake(take(0)).withTake(take(1)).withTake(take(0, 5000))
        assertEquals(2, bank.takes.size)
        assertEquals(8000, bank.totalMs)
        assertEquals(2, bank.nextUnrecorded(0, 4))
        assertEquals(3, bank.nextUnrecorded(3, 4))
        bank = bank.withTake(take(2)).withTake(take(3))
        assertNull(bank.nextUnrecorded(0, 4))
        assertEquals(listOf("a", "Mum's saying"), bank.addPrompt("  Mum's   saying ").prompts(listOf("a")))
    }

    @Test
    fun `training export writes ljspeech layout with checksums`() {
        val consent = VoiceConsent("Elizabeth", true, VoiceConsent.statementFor("Elizabeth", true), 1)
        val bank = VoiceBank(consent).withTake(take(1)).withTake(take(0))
        val out = ByteArrayOutputStream()
        var written = 0
        Backup.write(out, "correct horse".toCharArray()) { w ->
            written = TrainingExport.write(w, bank, listOf("Hello there.", "A | pipe"), 5) { byteArrayOf(it.promptIndex.toByte()) }
        }
        assertEquals(2, written)
        val entries = HashMap<String, ByteArray>()
        Backup.read(ByteArrayInputStream(out.toByteArray()), "correct horse".toCharArray()) { n, b -> entries[n] = b }
        assertEquals("utt00001|Hello there.|Hello there.\nutt00002|A   pipe|A   pipe\n", entries.getValue("metadata.csv").decodeToString())
        assertTrue(entries.containsKey("wavs/utt00002.wav"))
        assertTrue(entries.getValue("manifest.json").decodeToString().contains("\"speakerName\": \"Elizabeth\""))
    }

    @Test
    fun `export without consent is refused`() {
        assertFailsWith<IllegalArgumentException> {
            Backup.write(ByteArrayOutputStream(), "correct horse".toCharArray()) { w ->
                TrainingExport.write(w, VoiceBank().withTake(take(0)), listOf("x"), 0) { ByteArray(1) }
            }
        }
    }
}
