package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.keyguard.Keyguard
import uk.elizabeth.aac.core.scan.ScanMode
import uk.elizabeth.aac.core.scan.ScanSettings
import uk.elizabeth.aac.core.scan.Scanner
import uk.elizabeth.aac.core.touch.Bounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanTest {
    private val rows = listOf(listOf("speak"), listOf("a", "b", "c"), listOf("d", "e"))

    @Test
    fun `auto scan moves by itself and chooses row then button`() {
        val s = Scanner(ScanSettings(mode = ScanMode.AUTO, intervalMs = 1000, firstStepExtraMs = 500))
        s.setRows(rows, 0)
        assertEquals(listOf("speak"), s.highlighted)
        s.tick(1000)
        assertEquals(listOf("speak"), s.highlighted) // first step has extra time
        s.tick(1500)
        assertEquals(listOf("a", "b", "c"), s.highlighted)
        assertNull(s.choose(1600))
        assertEquals(listOf("a"), s.highlighted)
        s.tick(3100)
        assertEquals(listOf("b"), s.highlighted)
        assertEquals("b", s.choose(3200))
        assertEquals(listOf("speak"), s.highlighted)
    }

    @Test
    fun `a row with one button is pressed directly`() {
        val s = Scanner(ScanSettings(mode = ScanMode.STEP))
        s.setRows(rows, 0)
        assertEquals("speak", s.choose(10))
    }

    @Test
    fun `step scan returns to rows after passes without a choice`() {
        val s = Scanner(ScanSettings(mode = ScanMode.STEP, passesBeforeBack = 1))
        s.setRows(rows, 0)
        s.move(1); s.move(2)
        assertEquals(listOf("d", "e"), s.highlighted)
        s.choose(3)
        assertEquals(listOf("d"), s.highlighted)
        s.move(4); s.move(5)
        assertEquals(listOf("d", "e"), s.highlighted)
        s.tick(99_999) // STEP mode never moves by itself
        assertEquals(listOf("d", "e"), s.highlighted)
    }

    @Test
    fun `layout groups buttons into rows`() {
        val targets = mapOf(
            "b" to Bounds(110f, 0f, 200f, 100f),
            "a" to Bounds(0f, 0f, 100f, 100f),
            "c" to Bounds(0f, 110f, 100f, 200f),
            "tall" to Bounds(210f, 5f, 300f, 200f),
        )
        // "tall" starts level with row 1, but its centre is in row 2.
        assertEquals(listOf(listOf("a", "b"), listOf("c", "tall")), Scanner.rowsOf(targets))
    }

    @Test
    fun `keyguard is drawn at real size with inset holes`() {
        val svg = Keyguard.svg(mapOf("a" to Bounds(0f, 0f, 254f, 254f)), 2540, 1270, xdpi = 254f, ydpi = 254f, insetMm = 1.5)
        assertTrue(svg.contains("""width="254.00mm" height="127.00mm""""), svg)
        assertTrue(svg.contains("""<rect x="1.50" y="1.50" width="22.40" height="22.40""""), svg)
    }
}
