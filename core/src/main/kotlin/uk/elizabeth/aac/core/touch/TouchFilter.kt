package uk.elizabeth.aac.core.touch

/**
 * Turns raw finger events into deliberate button presses, filtering out the accidental
 * touches that come with tremor, weakness or reduced accuracy.
 *
 * Only one finger is tracked; the UI ignores extra contacts such as a resting palm.
 * Targets are button ids (null = no button). Times are milliseconds from a monotonic clock.
 * Each event returns the id of the button pressed, or null.
 */
class TouchFilter(settings: TouchSettings = TouchSettings()) {

    var settings: TouchSettings = settings.sanitised()
        set(value) {
            field = value.sanitised()
        }

    /** The button currently under the finger, after slip filtering. Used for highlighting. */
    var activeTarget: String? = null
        private set

    private var down = false
    private var firstTarget: String? = null
    private var activeSince = 0L
    private var hasPending = false
    private var pendingTarget: String? = null
    private var pendingSince = 0L
    private var firedThisTarget = false
    private var lastPressAt = Long.MIN_VALUE / 2

    fun onDown(target: String?, timeMs: Long): String? {
        down = true
        firstTarget = target
        activeTarget = target
        activeSince = timeMs
        hasPending = false
        firedThisTarget = false
        if (settings.selectOn == SelectOn.FIRST_CONTACT && target != null) return press(target, timeMs)
        return null
    }

    fun onMove(target: String?, timeMs: Long): String? {
        if (!down) return null
        updateTarget(target, timeMs)
        return tick(timeMs)
    }

    /** Call regularly while the finger is down, so dwell presses fire without movement. */
    fun tick(timeMs: Long): String? {
        if (!down || settings.selectOn != SelectOn.DWELL) return null
        commitPendingIfDue(timeMs)
        val target = activeTarget ?: return null
        if (!firedThisTarget && timeMs - activeSince >= settings.dwellMs) return press(target, timeMs)
        return null
    }

    fun onUp(target: String?, timeMs: Long): String? {
        if (!down) return null
        updateTarget(target, timeMs)
        commitPendingIfDue(timeMs)
        down = false
        var result: String? = null
        if (settings.selectOn == SelectOn.RELEASE) {
            val chosen = if (settings.slideToCorrect) activeTarget else firstTarget?.takeIf { it == activeTarget }
            if (chosen != null && timeMs - activeSince >= settings.minHoldMs) result = press(chosen, timeMs)
        }
        activeTarget = null
        hasPending = false
        return result
    }

    fun onCancel() {
        down = false
        activeTarget = null
        hasPending = false
    }

    /** Dwell progress from 0 to 1 for the highlighted button, for drawing a progress ring. */
    fun dwellProgress(timeMs: Long): Float {
        if (!down || settings.selectOn != SelectOn.DWELL || activeTarget == null || firedThisTarget) return 0f
        return ((timeMs - activeSince).toFloat() / settings.dwellMs).coerceIn(0f, 1f)
    }

    private fun updateTarget(target: String?, timeMs: Long) {
        if (target == activeTarget) {
            hasPending = false // came back before the slip grace ran out
            return
        }
        if (!hasPending || pendingTarget != target) {
            hasPending = true
            pendingTarget = target
            pendingSince = timeMs
        }
        commitPendingIfDue(timeMs)
    }

    private fun commitPendingIfDue(timeMs: Long) {
        if (!hasPending) return
        // Arriving on a button from empty space is never a slip.
        if (activeTarget == null || timeMs - pendingSince >= settings.slipGraceMs) {
            activeTarget = pendingTarget
            activeSince = pendingSince
            hasPending = false
            firedThisTarget = false
        }
    }

    private fun press(target: String, timeMs: Long): String? {
        if (timeMs - lastPressAt < settings.repeatGuardMs) return null
        lastPressAt = timeMs
        firedThisTarget = true
        return target
    }
}
