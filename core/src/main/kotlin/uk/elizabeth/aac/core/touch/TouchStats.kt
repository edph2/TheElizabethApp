package uk.elizabeth.aac.core.touch

import kotlinx.serialization.Serializable
import uk.elizabeth.aac.core.model.AppSettings

enum class TouchEvent {
    /** A button was pressed. */
    PRESSED,

    /** Undo, Delete word, Backspace or Clear straight after another press: probably a wrong press. */
    CORRECTION,

    /** A touch landed between buttons and was matched to the nearest one. */
    SNAPPED,

    /** A touch landed too far from any button. */
    MISSED,

    /** A touch was too short to count (a brush or bump). */
    TOO_SHORT,

    /** A second press straight after the first was ignored (bounce or tremor). */
    REPEAT_BLOCKED,
}

/**
 * Counts of how touches go, kept on the tablet. Only counts: no words, no positions, no times
 * of day. Collected only when a carer switches it on, and used only to suggest settings.
 */
@Serializable
data class TouchStats(
    val sinceMillis: Long = 0,
    val presses: Int = 0,
    val corrections: Int = 0,
    val snapped: Int = 0,
    val missed: Int = 0,
    val tooShort: Int = 0,
    val repeatBlocked: Int = 0,
) {
    fun add(event: TouchEvent): TouchStats = when (event) {
        TouchEvent.PRESSED -> copy(presses = presses + 1)
        TouchEvent.CORRECTION -> copy(corrections = corrections + 1)
        TouchEvent.SNAPPED -> copy(snapped = snapped + 1)
        TouchEvent.MISSED -> copy(missed = missed + 1)
        TouchEvent.TOO_SHORT -> copy(tooShort = tooShort + 1)
        TouchEvent.REPEAT_BLOCKED -> copy(repeatBlocked = repeatBlocked + 1)
    }
}

/** A suggested settings change, shown to a carer who can apply it or ignore it. */
class Suggestion(val id: String, val reason: String, val change: String, val apply: (AppSettings) -> AppSettings)

/**
 * Suggests touch settings from [TouchStats]. Settings are never changed automatically:
 * sudden changes would break her motor memory, so a person always decides.
 */
object TouchAdvisor {
    const val MIN_PRESSES = 150

    fun suggestions(stats: TouchStats, settings: AppSettings): List<Suggestion> {
        if (stats.presses < MIN_PRESSES) return emptyList()
        val t = settings.touch
        val touches = (stats.presses + stats.missed).toDouble()
        val correctionRate = stats.corrections.toDouble() / stats.presses
        val missRate = stats.missed / touches
        val snapRate = stats.snapped / touches
        val out = mutableListOf<Suggestion>()

        if (correctionRate > 0.25 && t.selectOn != SelectOn.DWELL && !settings.scan.enabled) {
            out += Suggestion(
                "try-dwell", pct(correctionRate) + " of presses are corrected straight away.",
                "Try pressing by resting on a button (dwell), or switch scanning",
            ) { it.copy(touch = it.touch.copy(selectOn = SelectOn.DWELL)) }
        } else if (correctionRate > 0.10) {
            if (t.selectOn == SelectOn.FIRST_CONTACT) {
                out += Suggestion(
                    "use-release", pct(correctionRate) + " of presses are corrected straight away.",
                    "Press when the finger lifts, so she can slide to the right button first",
                ) { it.copy(touch = it.touch.copy(selectOn = SelectOn.RELEASE)) }
            } else if (t.selectOn == SelectOn.RELEASE && t.slipGraceMs < 400) {
                val grace = minOf(400L, t.slipGraceMs + 75)
                out += Suggestion(
                    "slip-grace", pct(correctionRate) + " of presses are corrected straight away.",
                    "Ignore slips shorter than $grace ms (now ${t.slipGraceMs} ms)",
                ) { it.copy(touch = it.touch.copy(slipGraceMs = grace)) }
            }
            if (settings.gridColumns > 3) {
                out += Suggestion(
                    "fewer-columns", pct(correctionRate) + " of presses are corrected straight away.",
                    "Make phrase buttons bigger: ${settings.gridColumns - 1} columns instead of ${settings.gridColumns}",
                ) { it.copy(gridColumns = it.gridColumns - 1) }
            }
        }
        if (missRate > 0.10 && t.snapRadiusDp < 48f) {
            val radius = minOf(48f, t.snapRadiusDp + 8f)
            out += Suggestion(
                "snap-radius", pct(missRate) + " of touches land too far from any button.",
                "Count touches up to ${radius.toInt()} dp from a button (now ${t.snapRadiusDp.toInt()} dp)",
            ) { it.copy(touch = it.touch.copy(snapRadiusDp = radius)) }
        } else if (snapRate > 0.30 && settings.gridRows > 2) {
            out += Suggestion(
                "fewer-rows", pct(snapRate) + " of touches land between buttons.",
                "Make phrase buttons taller: ${settings.gridRows - 1} rows instead of ${settings.gridRows}",
            ) { it.copy(gridRows = it.gridRows - 1) }
        }
        if (stats.repeatBlocked.toDouble() / stats.presses > 0.15 && t.repeatGuardMs < 1500) {
            val guard = minOf(1500L, t.repeatGuardMs + 200)
            out += Suggestion(
                "repeat-guard", "Many double presses are being caught.",
                "Ignore repeat presses for $guard ms (now ${t.repeatGuardMs} ms)",
            ) { it.copy(touch = it.touch.copy(repeatGuardMs = guard)) }
        }
        return out
    }

    private fun pct(rate: Double) = "${(rate * 100).toInt()}%"
}
