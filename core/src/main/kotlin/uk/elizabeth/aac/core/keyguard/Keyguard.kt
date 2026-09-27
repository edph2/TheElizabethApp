package uk.elizabeth.aac.core.keyguard

import uk.elizabeth.aac.core.touch.Bounds
import java.util.Locale

/**
 * A keyguard is a sheet of acrylic with a hole over each button. It rests on the screen so a
 * finger can rest and slide without pressing anything, and guides the finger into the
 * button. This makes an SVG cutting template, at real size, from the buttons' positions.
 *
 * Holes are made slightly smaller than the buttons ([insetMm]) so the edges of the guard
 * cover the gaps between buttons.
 */
object Keyguard {
    private const val MM_PER_INCH = 25.4

    fun svg(
        targets: Map<String, Bounds>,
        screenWidthPx: Int,
        screenHeightPx: Int,
        xdpi: Float,
        ydpi: Float,
        insetMm: Double = 1.5,
        cornerMm: Double = 3.0,
    ): String {
        require(xdpi > 0 && ydpi > 0) { "Screen density unknown" }
        fun mmX(px: Float) = px / xdpi * MM_PER_INCH
        fun mmY(px: Float) = px / ydpi * MM_PER_INCH
        val width = mmX(screenWidthPx.toFloat())
        val height = mmY(screenHeightPx.toFloat())
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${f(width)}mm" height="${f(height)}mm" viewBox="0 0 ${f(width)} ${f(height)}">""").append('\n')
        sb.append("  <title>Keyguard template: cut along the red lines. Print or cut at 100% scale.</title>\n")
        sb.append("""  <rect x="0" y="0" width="${f(width)}" height="${f(height)}" fill="none" stroke="#ff0000" stroke-width="0.1"/>""").append('\n')
        for ((_, b) in targets.entries.sortedWith(compareBy({ it.value.top }, { it.value.left }))) {
            val x = mmX(b.left) + insetMm
            val y = mmY(b.top) + insetMm
            val w = mmX(b.right - b.left) - 2 * insetMm
            val h = mmY(b.bottom - b.top) - 2 * insetMm
            if (w <= 2 || h <= 2) continue
            val r = minOf(cornerMm, w / 2, h / 2)
            sb.append("""  <rect x="${f(x)}" y="${f(y)}" width="${f(w)}" height="${f(h)}" rx="${f(r)}" fill="none" stroke="#ff0000" stroke-width="0.1"/>""").append('\n')
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun f(v: Double) = String.format(Locale.ROOT, "%.2f", v)
}
