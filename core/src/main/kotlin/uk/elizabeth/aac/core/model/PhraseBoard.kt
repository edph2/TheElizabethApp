package uk.elizabeth.aac.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** A phrase button. If [recordingId] is set, her own recording is played instead of the synthetic voice. */
@Serializable
data class Phrase(
    val id: String = newId(),
    val text: String,
    val recordingId: String? = null,
)

@Serializable
data class Category(
    val id: String = newId(),
    val label: String,
    val phrases: List<Phrase> = emptyList(),
)

/** All phrases: the always-visible quick replies plus the phrase pages, grouped by topic. */
@Serializable
data class PhraseBoard(
    val quickReplies: List<Phrase>,
    val categories: List<Category>,
) {
    fun allPhrases(): List<Phrase> = quickReplies + categories.flatMap { it.phrases }

    fun findPhrase(id: String): Phrase? = allPhrases().firstOrNull { it.id == id }

    fun addPhrase(categoryId: String, text: String): PhraseBoard {
        val clean = text.trim()
        if (clean.isEmpty()) return this
        return mapCategory(categoryId) { it.copy(phrases = it.phrases + Phrase(text = clean)) }
    }

    fun updatePhrase(phraseId: String, transform: (Phrase) -> Phrase): PhraseBoard = copy(
        quickReplies = quickReplies.map { if (it.id == phraseId) transform(it) else it },
        categories = categories.map { c -> c.copy(phrases = c.phrases.map { if (it.id == phraseId) transform(it) else it }) },
    )

    fun removePhrase(phraseId: String): PhraseBoard = copy(
        categories = categories.map { c -> c.copy(phrases = c.phrases.filterNot { it.id == phraseId }) },
    )

    fun movePhrase(phraseId: String, offset: Int): PhraseBoard = copy(
        categories = categories.map { c ->
            val i = c.phrases.indexOfFirst { it.id == phraseId }
            val j = i + offset
            if (i < 0 || j !in c.phrases.indices) c
            else c.copy(phrases = c.phrases.toMutableList().apply { add(j, removeAt(i)) })
        },
    )

    fun addCategory(label: String): PhraseBoard {
        val clean = label.trim()
        return if (clean.isEmpty()) this else copy(categories = categories + Category(label = clean))
    }

    fun renameCategory(categoryId: String, label: String): PhraseBoard =
        if (label.isBlank()) this else mapCategory(categoryId) { it.copy(label = label.trim()) }

    fun removeCategory(categoryId: String): PhraseBoard =
        copy(categories = categories.filterNot { it.id == categoryId })

    /** Recording ids that are no longer used by any phrase, so their files can be deleted. */
    fun unusedRecordings(existing: Collection<String>): Set<String> =
        existing.toSet() - allPhrases().mapNotNull { it.recordingId }.toSet()

    private fun mapCategory(id: String, transform: (Category) -> Category) =
        copy(categories = categories.map { if (it.id == id) transform(it) else it })

    companion object {
        fun default(): PhraseBoard = PhraseBoard(
            quickReplies = listOf("Yes", "No", "Wait", "Help", "Thank you").map { Phrase(id = "quick-" + it.lowercase().replace(' ', '-'), text = it) },
            categories = listOf(
                category(
                    "Needs",
                    "I need help, please", "Could I have a drink, please?", "I need the toilet",
                    "I'm in pain", "Please move me", "I'm too hot", "I'm too cold", "I'm tired",
                    "Could I have something to eat?", "Please adjust my pillow", "Please call someone",
                    "I need my medicine", "Could you open the window?", "Please turn the light off",
                    "Please turn the television on", "Please pass me my glasses",
                ),
                category(
                    "Feelings",
                    "I'm fine, thank you", "I'm happy", "I'm sad", "I'm worried", "I'm frustrated",
                    "I'm uncomfortable", "I feel sick", "I'm scared", "I feel much better today",
                    "I'm not feeling well", "I love you", "I'm bored",
                ),
                category(
                    "Chat",
                    "Hello, how are you?", "Lovely to see you", "What have you been up to?",
                    "Tell me more", "That's wonderful", "I don't agree", "That's funny",
                    "Goodbye, see you soon", "Please be patient, I'm using a communication aid",
                    "Can you say that again?", "I'm still thinking", "Never mind",
                ),
                category(
                    "Care",
                    "Where does it hurt? Let me show you", "It hurts here", "Please be gentle",
                    "Please stop", "That's better", "Please wait a moment", "I'm having trouble breathing",
                    "I'm having trouble swallowing", "Please call the doctor", "When is my appointment?",
                    "Please explain that again", "I don't want that",
                ),
                category(
                    "Questions",
                    "What time is it?", "What day is it?", "Who is coming today?", "When will you be back?",
                    "Where are you going?", "What's for lunch?", "How was your day?", "Is everything all right?",
                ),
            ),
        )

        private fun category(label: String, vararg phrases: String) =
            Category(id = "cat-" + label.lowercase(), label = label, phrases = phrases.map { Phrase(text = it) })
    }
}

internal fun newId(): String = UUID.randomUUID().toString()
