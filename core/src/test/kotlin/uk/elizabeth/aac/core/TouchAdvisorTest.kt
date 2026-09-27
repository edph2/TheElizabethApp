package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.model.AppSettings
import uk.elizabeth.aac.core.touch.SelectOn
import uk.elizabeth.aac.core.touch.TouchAdvisor
import uk.elizabeth.aac.core.touch.TouchEvent
import uk.elizabeth.aac.core.touch.TouchFilter
import uk.elizabeth.aac.core.touch.TouchSettings
import uk.elizabeth.aac.core.touch.TouchStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TouchAdvisorTest {
    private fun stats(presses: Int, corrections: Int = 0, missed: Int = 0, snapped: Int = 0) =
        TouchStats(presses = presses, corrections = corrections, missed = missed, snapped = snapped)

    @Test
    fun `no suggestions until there is enough to go on`() {
        assertTrue(TouchAdvisor.suggestions(stats(100, corrections = 90), AppSettings()).isEmpty())
    }

    @Test
    fun `frequent corrections suggest more slip grace and bigger buttons`() {
        val ids = TouchAdvisor.suggestions(stats(200, corrections = 30), AppSettings()).map { it.id }
        assertEquals(listOf("slip-grace", "fewer-columns"), ids)
        val applied = TouchAdvisor.suggestions(stats(200, corrections = 30), AppSettings()).first().apply(AppSettings())
        assertEquals(225, applied.touch.slipGraceMs)
    }

    @Test
    fun `very frequent corrections suggest dwell`() {
        val s = TouchAdvisor.suggestions(stats(200, corrections = 80), AppSettings()).single()
        assertEquals(SelectOn.DWELL, s.apply(AppSettings()).touch.selectOn)
    }

    @Test
    fun `misses suggest a larger near-miss distance`() {
        assertEquals(listOf("snap-radius"), TouchAdvisor.suggestions(stats(200, missed = 40), AppSettings()).map { it.id })
    }

    @Test
    fun `counts are only counts`() {
        val s = TouchStats().add(TouchEvent.PRESSED).add(TouchEvent.PRESSED).add(TouchEvent.MISSED)
        assertEquals(2, s.presses)
        assertEquals(1, s.missed)
    }

    @Test
    fun `filter reports why a touch did not count`() {
        val f = TouchFilter(TouchSettings(minHoldMs = 100, repeatGuardMs = 500))
        f.onDown("A", 0); f.onUp("A", 20)
        assertEquals(TouchFilter.Outcome.TOO_SHORT, f.lastOutcome)
        f.onDown("A", 1000); f.onUp("A", 1200)
        assertEquals(TouchFilter.Outcome.PRESSED, f.lastOutcome)
        f.onDown("A", 1300); f.onUp("A", 1500)
        assertEquals(TouchFilter.Outcome.REPEAT_BLOCKED, f.lastOutcome)
    }
}
