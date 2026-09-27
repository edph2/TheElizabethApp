package uk.elizabeth.aac.core.scan

import kotlinx.serialization.Serializable
import uk.elizabeth.aac.core.touch.Bounds

@Serializable
enum class ScanMode {
    /** One switch: the highlight moves on by itself; pressing the switch chooses. */
    AUTO,

    /** Two switches: one moves the highlight, the other chooses. */
    STEP,
}

@Serializable
data class ScanSettings(
    val enabled: Boolean = false,
    val mode: ScanMode = ScanMode.AUTO,
    /** How long each row or button stays highlighted in AUTO mode. */
    val intervalMs: Long = 1500,
    /** Extra time on the first row or button, to give time to react after a choice. */
    val firstStepExtraMs: Long = 500,
    /** In a row, go back to scanning rows after this many passes without a choice. */
    val passesBeforeBack: Int = 2,
) {
    fun sanitised() = copy(
        intervalMs = intervalMs.coerceIn(400, 6000),
        firstStepExtraMs = firstStepExtraMs.coerceIn(0, 3000),
        passesBeforeBack = passesBeforeBack.coerceIn(1, 5),
    )
}

/**
 * Row-then-button switch scanning, for when touch is no longer reliable. The rows are
 * highlighted in turn; choosing a row then highlights its buttons in turn; choosing a button
 * presses it. Works with one switch (AUTO) or two (STEP).
 */
class Scanner(settings: ScanSettings = ScanSettings()) {
    var settings = settings.sanitised()
        set(value) {
            field = value.sanitised()
        }

    private var rows: List<List<String>> = emptyList()
    private var inRow = false
    private var row = 0
    private var item = 0
    private var passes = 0
    private var stepStartedAt = Long.MIN_VALUE
    private var firstStep = true

    /** The buttons currently highlighted: a whole row, or one button. */
    val highlighted: List<String>
        get() = when {
            rows.isEmpty() -> emptyList()
            inRow -> listOfNotNull(rows[row].getOrNull(item))
            else -> rows[row]
        }

    /** Updates the rows (the screen changed). Restarts from the first row if they differ. */
    fun setRows(newRows: List<List<String>>, nowMs: Long) {
        val clean = newRows.filter { it.isNotEmpty() }
        if (clean == rows) return
        rows = clean
        restart(nowMs)
    }

    fun restart(nowMs: Long) {
        inRow = false
        row = 0
        item = 0
        passes = 0
        firstStep = true
        stepStartedAt = nowMs
    }

    /** Advances the highlight automatically in AUTO mode. Call regularly. */
    fun tick(nowMs: Long) {
        if (rows.isEmpty() || settings.mode != ScanMode.AUTO) return
        val wait = settings.intervalMs + if (firstStep) settings.firstStepExtraMs else 0
        if (nowMs - stepStartedAt >= wait) move(nowMs)
    }

    /** The "move" switch (STEP mode). */
    fun move(nowMs: Long) {
        if (rows.isEmpty()) return
        firstStep = false
        stepStartedAt = nowMs
        if (inRow) {
            item++
            if (item >= rows[row].size) {
                item = 0
                passes++
                if (passes >= settings.passesBeforeBack) {
                    inRow = false
                    passes = 0
                }
            }
        } else {
            row = (row + 1) % rows.size
        }
    }

    /** The "choose" switch. Returns the button to press, if a button (not a row) was chosen. */
    fun choose(nowMs: Long): String? {
        if (rows.isEmpty()) return null
        if (!inRow) {
            if (rows[row].size == 1) {
                val only = rows[row][0]
                restart(nowMs)
                return only
            }
            inRow = true
            item = 0
            passes = 0
            firstStep = true
            stepStartedAt = nowMs
            return null
        }
        val chosen = rows[row].getOrNull(item)
        restart(nowMs)
        return chosen
    }

    companion object {
        /**
         * Groups on-screen buttons into rows, top to bottom and left to right. A button joins a
         * row if its vertical centre lies within the row's first button.
         */
        fun rowsOf(targets: Map<String, Bounds>): List<List<String>> {
            val sorted = targets.entries.sortedWith(compareBy({ it.value.top }, { it.value.left }))
            val rows = mutableListOf<MutableList<Map.Entry<String, Bounds>>>()
            for (entry in sorted) {
                val centre = (entry.value.top + entry.value.bottom) / 2
                val row = rows.firstOrNull { r -> centre >= r[0].value.top && centre < r[0].value.bottom }
                if (row != null) row += entry else rows += mutableListOf(entry)
            }
            return rows.map { r -> r.sortedBy { it.value.left }.map { it.key } }
        }
    }
}
