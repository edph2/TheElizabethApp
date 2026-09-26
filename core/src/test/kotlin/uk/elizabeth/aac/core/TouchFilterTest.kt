package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.touch.Bounds
import uk.elizabeth.aac.core.touch.SelectOn
import uk.elizabeth.aac.core.touch.TargetRegistry
import uk.elizabeth.aac.core.touch.TouchFilter
import uk.elizabeth.aac.core.touch.TouchSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TouchFilterTest {
    private val release = TouchSettings(selectOn = SelectOn.RELEASE, minHoldMs = 80, repeatGuardMs = 400, slipGraceMs = 150)

    @Test
    fun `release selects the button under the finger`() {
        val f = TouchFilter(release)
        assertNull(f.onDown("A", 0))
        assertEquals("A", f.onUp("A", 200))
    }

    @Test
    fun `a brush shorter than the minimum hold is ignored`() {
        val f = TouchFilter(release)
        f.onDown("A", 0)
        assertNull(f.onUp("A", 30))
    }

    @Test
    fun `sliding to another button and resting there selects it`() {
        val f = TouchFilter(release)
        f.onDown("A", 0)
        f.onMove("B", 100)
        f.onMove("B", 300)
        assertEquals("B", f.onUp("B", 400))
    }

    @Test
    fun `a slip onto a neighbour just before lifting is ignored`() {
        val f = TouchFilter(release)
        f.onDown("A", 0)
        f.onMove("B", 300) // tremor as the finger lifts
        assertEquals("A", f.onUp("B", 350))
    }

    @Test
    fun `a brief slip away and back does not reset the hold`() {
        val f = TouchFilter(release.copy(minHoldMs = 300))
        f.onDown("A", 0)
        f.onMove("B", 200)
        f.onMove("A", 250)
        assertEquals("A", f.onUp("A", 320))
    }

    @Test
    fun `without slide to correct the first button must still be under the finger`() {
        val f = TouchFilter(release.copy(slideToCorrect = false))
        f.onDown("A", 0)
        f.onMove("B", 100)
        assertNull(f.onUp("B", 500))
        f.onDown("A", 1000)
        assertEquals("A", f.onUp("A", 1200))
    }

    @Test
    fun `repeat guard blocks a double press from tremor`() {
        val f = TouchFilter(release)
        f.onDown("A", 0)
        assertEquals("A", f.onUp("A", 100))
        f.onDown("A", 150)
        assertNull(f.onUp("A", 300))
        f.onDown("A", 700)
        assertEquals("A", f.onUp("A", 800))
    }

    @Test
    fun `dwell fires once after resting and not again until moving`() {
        val f = TouchFilter(TouchSettings(selectOn = SelectOn.DWELL, dwellMs = 800, repeatGuardMs = 0, slipGraceMs = 100))
        f.onDown("A", 0)
        assertNull(f.tick(500))
        assertEquals(0.75f, f.dwellProgress(600), 0.001f)
        assertEquals("A", f.tick(800))
        assertNull(f.tick(2000))
        f.onMove("B", 2100)
        assertNull(f.tick(2150))
        assertEquals("B", f.tick(2100 + 800))
        assertNull(f.onUp("B", 3000))
    }

    @Test
    fun `first contact selects immediately`() {
        val f = TouchFilter(TouchSettings(selectOn = SelectOn.FIRST_CONTACT))
        assertEquals("A", f.onDown("A", 0))
        assertNull(f.onUp("A", 100))
    }

    @Test
    fun `touching empty space selects nothing`() {
        val f = TouchFilter(release)
        f.onDown(null, 0)
        assertNull(f.onUp(null, 300))
    }

    @Test
    fun `settings are clamped to safe ranges`() {
        val f = TouchFilter(TouchSettings(dwellMs = 1, repeatGuardMs = -5))
        assertEquals(200, f.settings.dwellMs)
        assertEquals(0, f.settings.repeatGuardMs)
    }

    @Test
    fun `registry snaps touches in gaps to the nearest button`() {
        val r = TargetRegistry()
        r.register("A", Bounds(0f, 0f, 100f, 100f))
        r.register("B", Bounds(120f, 0f, 220f, 100f))
        assertEquals("A", r.hitTest(50f, 50f, 24f))
        assertEquals("A", r.hitTest(105f, 50f, 24f))
        assertEquals("B", r.hitTest(115f, 50f, 24f))
        assertNull(r.hitTest(500f, 500f, 24f))
        r.unregister("B")
        assertEquals("A", r.hitTest(115f, 50f, 24f))
    }
}
