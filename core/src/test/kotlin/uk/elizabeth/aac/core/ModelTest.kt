package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.audio.PcmAudio
import uk.elizabeth.aac.core.audio.QualityIssue
import uk.elizabeth.aac.core.audio.RecordingQuality
import uk.elizabeth.aac.core.audio.Wav
import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.aac.core.model.History
import uk.elizabeth.aac.core.model.Paging
import uk.elizabeth.aac.core.model.PhraseBoard
import uk.elizabeth.aac.core.privacy.PinHasher
import uk.elizabeth.aac.core.privacy.PrivacyEventType
import uk.elizabeth.aac.core.privacy.PrivacyLog
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun `phrase board edits`() {
        val board = PhraseBoard.default()
        val cat = board.categories.first()
        val added = board.addPhrase(cat.id, "  Where is my book?  ")
        val phrase = added.categories.first().phrases.last()
        assertEquals("Where is my book?", phrase.text)
        val recorded = added.updatePhrase(phrase.id) { it.copy(recordingId = "r1") }
        assertEquals(setOf("r2"), recorded.unusedRecordings(listOf("r1", "r2")))
        assertFalse(recorded.removePhrase(phrase.id).allPhrases().any { it.id == phrase.id })
        val moved = added.movePhrase(phrase.id, -1).categories.first().phrases
        assertEquals(phrase.id, moved[moved.size - 2].id)
    }

    @Test
    fun `paging never scrolls past the end`() {
        val items = (1..10).toList()
        assertEquals(3, Paging.pageCount(10, 4))
        assertEquals(listOf(9, 10), Paging.page(items, 2, 4))
        assertEquals(listOf(9, 10), Paging.page(items, 99, 4))
        assertEquals(1, Paging.pageCount(0, 4))
    }

    @Test
    fun `history suggests frequent whole messages`() {
        var h = History()
        h = h.add("Could I have some tea", 1, 100)
        h = h.add("Could I have a biscuit", 2, 100)
        h = h.add("Could I have some tea", 3, 100)
        assertEquals(listOf("Could I have some tea", "Could I have a biscuit"), h.suggest("could i", 5))
        assertEquals(listOf("Could I have some tea", "Could I have a biscuit"), h.recent(5))
        assertEquals(2, h.add("x", 4, 2).entries.size)
    }

    @Test
    fun `privacy log detects tampering`() {
        var log = PrivacyLog()
        log = log.append(PrivacyEventType.APP_FIRST_RUN, 1)
        log = log.append(PrivacyEventType.DATA_EXPORTED, 2)
        log = log.append(PrivacyEventType.LEARNING_DISABLED, 3)
        assertTrue(log.verify())
        val edited = log.copy(events = log.events.toMutableList().also { it[1] = it[1].copy(timeMillis = 99) })
        assertFalse(edited.verify())
        val deleted = log.copy(events = log.events.filterIndexed { i, _ -> i != 1 })
        assertFalse(deleted.verify())
    }

    @Test
    fun `pin hash verifies only the right pin`() {
        val stored = PinHasher.hash("2468")
        assertTrue(PinHasher.verify("2468", stored))
        assertFalse(PinHasher.verify("2469", stored))
        assertFalse(PinHasher.verify("2468", "garbage"))
    }

    @Test
    fun `app data round trips through json`() {
        val data = AppData(history = History().add("Hello", 5, 10))
        assertEquals(data, AppData.fromJson(data.toJson()))
    }

    @Test
    fun `wav round trip`() {
        val audio = PcmAudio(ShortArray(1000) { (it * 7).toShort() }, 22_050)
        val decoded = Wav.decode(Wav.encode(audio))
        assertEquals(22_050, decoded.sampleRate)
        assertContentEquals(audio.samples, decoded.samples)
    }

    @Test
    fun `quality check flags silence, clipping and passes clean speech`() {
        val rate = 22_050
        val silent = PcmAudio(ShortArray(rate), rate)
        assertTrue(QualityIssue.SILENT in RecordingQuality.check(silent).issues)

        val random = Random(1)
        val speechLike = PcmAudio(ShortArray(rate * 2) { i ->
            val t = i.toDouble() / rate
            val burst = if ((t * 4).toInt() % 2 == 0) 8000.0 * sin(2 * PI * 220 * t) else 0.0
            (burst + random.nextInt(-20, 20)).toInt().toShort()
        }, rate)
        assertTrue(RecordingQuality.check(speechLike).isGood, RecordingQuality.check(speechLike).toString())

        val clipped = PcmAudio(ShortArray(rate) { if (it % 2 == 0) 32767 else -32768 }, rate)
        assertTrue(QualityIssue.CLIPPING in RecordingQuality.check(clipped).issues)

        val trimmed = RecordingQuality.trimSilence(PcmAudio(ShortArray(rate) { if (it in 10_000..11_000) 5000 else 0 }, rate))
        assertTrue(trimmed.samples.size < 2 * 22_050 * 150 / 1000 + 1100)
    }
}
