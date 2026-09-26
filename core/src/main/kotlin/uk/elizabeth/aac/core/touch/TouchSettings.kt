package uk.elizabeth.aac.core.touch

import kotlinx.serialization.Serializable

/** When a touched button counts as pressed. */
@Serializable
enum class SelectOn {
    /** As soon as the finger lands. Fastest, but most prone to accidental presses. */
    FIRST_CONTACT,

    /** When the finger lifts. The finger can slide to the right button before lifting. */
    RELEASE,

    /** Once the finger has rested on a button for [TouchSettings.dwellMs]. No lift needed. */
    DWELL,
}

/**
 * Tuning for inaccurate or shaky touch. Every value is adjustable in Settings so it can
 * follow changes in her motor control.
 */
@Serializable
data class TouchSettings(
    val selectOn: SelectOn = SelectOn.RELEASE,
    /** The finger must rest on a button at least this long for it to count. Filters brushes. */
    val minHoldMs: Long = 80,
    /** How long to rest on a button in [SelectOn.DWELL] mode. */
    val dwellMs: Long = 900,
    /** After a press, further presses are ignored for this long. Stops tremor double presses. */
    val repeatGuardMs: Long = 450,
    /** Slipping onto a neighbouring button for less than this is ignored. */
    val slipGraceMs: Long = 150,
    /** Touches in the gap between buttons go to the nearest button within this distance. */
    val snapRadiusDp: Float = 24f,
    /** In RELEASE mode: true = button under the finger on lift; false = the button first touched. */
    val slideToCorrect: Boolean = true,
) {
    fun sanitised(): TouchSettings = copy(
        minHoldMs = minHoldMs.coerceIn(0, 2_000),
        dwellMs = dwellMs.coerceIn(200, 5_000),
        repeatGuardMs = repeatGuardMs.coerceIn(0, 3_000),
        slipGraceMs = slipGraceMs.coerceIn(0, 1_000),
        snapRadiusDp = snapRadiusDp.coerceIn(0f, 80f),
    )
}
