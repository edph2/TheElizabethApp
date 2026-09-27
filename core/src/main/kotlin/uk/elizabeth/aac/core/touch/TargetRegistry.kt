package uk.elizabeth.aac.core.touch

import kotlin.math.sqrt

/** An axis-aligned rectangle in screen pixels. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    /** Distance from a point to the nearest edge of this rectangle (0 if inside). */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * Knows where every on-screen button is, so a touch can be matched to a button even when it
 * lands slightly outside it. Buttons register when laid out and unregister when removed.
 */
class TargetRegistry {
    private val targets = LinkedHashMap<String, Bounds>()

    fun register(id: String, bounds: Bounds) {
        targets[id] = bounds
    }

    fun unregister(id: String) {
        targets.remove(id)
    }

    /** Where every button currently is. */
    fun snapshot(): Map<String, Bounds> = LinkedHashMap(targets)

    /**
     * The button containing the point, or else the nearest button within [snapRadiusPx].
     * Returns null if the touch is too far from any button.
     */
    fun hitTest(x: Float, y: Float, snapRadiusPx: Float): String? {
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for ((id, bounds) in targets) {
            if (bounds.contains(x, y)) return id
            val d = bounds.distanceTo(x, y)
            if (d <= snapRadiusPx && d < bestDistance) {
                best = id
                bestDistance = d
            }
        }
        return best
    }
}
