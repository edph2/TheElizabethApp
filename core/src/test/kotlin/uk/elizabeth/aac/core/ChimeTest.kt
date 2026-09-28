package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.audio.Chime
import kotlin.test.Test
import kotlin.test.assertTrue

class ChimeTest {
    @Test
    fun `chime is audible and not clipped`() {
        val chime = Chime.generate()
        val peak = chime.samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 15_000..32_000)
        assertTrue(chime.durationMs in 1390..1410)
    }
}
