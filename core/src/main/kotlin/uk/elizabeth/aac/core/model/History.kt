package uk.elizabeth.aac.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(val text: String, val timeMillis: Long)

/** Messages she has spoken, newest first, capped in size. Used for Recent and phrase prediction. */
@Serializable
data class History(val entries: List<HistoryEntry> = emptyList()) {

    fun add(text: String, timeMillis: Long, maxSize: Int): History {
        val clean = text.trim()
        if (clean.isEmpty()) return this
        return History((listOf(HistoryEntry(clean, timeMillis)) + entries).take(maxSize))
    }

    /** Distinct recent messages, newest first. */
    fun recent(count: Int): List<String> =
        entries.map { it.text }.distinctBy { it.lowercase() }.take(count)

    /**
     * Whole messages she has said before that start with what she has typed so far,
     * ranked by how often she says them, then by how recently.
     */
    fun suggest(typed: String, count: Int): List<String> {
        val prefix = typed.trim().lowercase()
        if (prefix.length < 2) return emptyList()
        return entries.withIndex()
            .filter { (_, e) -> e.text.lowercase().startsWith(prefix) && e.text.lowercase() != prefix }
            .groupBy { it.value.text.lowercase() }
            .map { (_, group) -> Triple(group.first().value.text, group.size, group.minOf { it.index }) }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .take(count)
            .map { it.first }
    }

    fun trimmedTo(maxSize: Int) = History(entries.take(maxSize))
}
