package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.model.KeyboardLayout
import uk.elizabeth.aac.core.model.PhraseBoard
import uk.elizabeth.aac.core.print.PaperBoard
import uk.elizabeth.aac.core.privacy.PinGuard
import uk.elizabeth.aac.core.text.Sentences
import uk.elizabeth.aac.core.voicebank.VoiceBank
import uk.elizabeth.voiceformat.VoiceConsent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GapsTest {
    @Test
    fun `sentences split at punctuation and long sentences at commas`() {
        assertEquals(listOf("Hello there.", "How are you?", "Fine!"), Sentences.split("Hello there. How are you?\nFine!"))
        val long = "one two three, " + "word ".repeat(60)
        val parts = Sentences.split(long, maxLength = 100)
        assertTrue(parts.all { it.length <= 100 }, parts.toString())
        assertEquals(long.split(Regex("\\s+")).filter { it.isNotEmpty() }, parts.joinToString(" ").split(Regex("\\s+")))
        assertEquals(emptyList(), Sentences.split("   "))
    }

    @Test
    fun `pin guard locks after repeated failures and doubles the wait`() {
        var g = PinGuard()
        repeat(4) { g = g.afterFailure(0) }
        assertFalse(g.isLocked(0))
        g = g.afterFailure(1_000)
        assertTrue(g.isLocked(1_000))
        assertEquals(30, g.secondsLeft(1_000))
        assertFalse(g.isLocked(31_000))
        g = g.afterFailure(31_000)
        assertEquals(60, g.secondsLeft(31_000))
        repeat(20) { g = g.afterFailure(0) }
        assertEquals(15 * 60, g.secondsLeft(0))
        assertEquals(PinGuard(), g.afterSuccess())
    }

    @Test
    fun `paper board contains every phrase and escapes text`() {
        val board = PhraseBoard.default().let { b -> b.addPhrase(b.categories.first().id, "Fish & <chips>") }
        val html = PaperBoard.html(board, KeyboardLayout.QWERTY)
        board.allPhrases().forEach { assertTrue(html.contains(it.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")), it.text) }
        assertTrue(html.contains("Fish &amp; &lt;chips&gt;"))
        assertFalse(html.contains("http"), "must not load anything from the network")
    }

    @Test
    fun `voices made from withdrawn recordings are recognised`() {
        val consent = VoiceConsent("Elizabeth", true, "I agree", 42)
        val bank = VoiceBank(consent)
        assertTrue(bank.isSourceOf(consent.copy(statement = "different wording")))
        assertFalse(bank.isSourceOf(consent.copy(timeMillis = 43)))
        assertFalse(bank.isSourceOf(null))
        assertFalse(VoiceBank().isSourceOf(consent))
    }
}
