package uk.elizabeth.aac.core.text

/**
 * The message being composed. Every change can be undone, because with unreliable touch
 * a wrong press should cost one press to fix, not several.
 */
class MessageEditor(initial: String = "", private val maxUndo: Int = 50) {

    var text: String = initial
        private set

    private val undoStack = ArrayDeque<String>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** The partly typed word at the end of the message ("" if the message ends in a space). */
    val currentWordPrefix: String
        get() = text.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '-' }

    /** The completed words of the current sentence, lower-cased, for next-word prediction. */
    val precedingWords: List<String>
        get() {
            val sentence = text.dropLast(currentWordPrefix.length).split('.', '!', '?', '\n').last()
            return tokenize(sentence)
        }

    fun typeCharacter(c: Char) {
        val next = if (c.isLetter() && startsSentence(text)) c.uppercaseChar() else c
        edit(text + next)
    }

    /**
     * Inserts a whole word, replacing the partly typed word if there is one, and adds a
     * trailing space so the next word can follow straight away.
     */
    fun insertWord(word: String) {
        val base = text.dropLast(currentWordPrefix.length)
        val cased = if (startsSentence(base)) word.replaceFirstChar { it.uppercaseChar() } else word
        val spacer = if (base.isNotEmpty() && !base.last().isWhitespace()) " " else ""
        edit(base + spacer + cased + " ")
    }

    /** Adds a phrase as its own sentence. */
    fun insertPhrase(phrase: String) {
        val trimmed = text.trimEnd()
        val separator = when {
            trimmed.isEmpty() -> ""
            trimmed.last() in ".!?" -> " "
            else -> ". "
        }
        edit(trimmed + separator + phrase.trim() + " ")
    }

    fun backspace() {
        if (text.isNotEmpty()) edit(text.dropLast(1))
    }

    /** Deletes the last word, and any spaces after it. */
    fun deleteWord() {
        if (text.isEmpty()) return
        edit(text.trimEnd().dropLastWhile { !it.isWhitespace() })
    }

    fun clear() {
        if (text.isNotEmpty()) edit("")
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        text = previous
        return true
    }

    private fun edit(newText: String) {
        if (newText == text) return
        undoStack.addLast(text)
        while (undoStack.size > maxUndo) undoStack.removeFirst()
        text = newText
    }

    private fun startsSentence(base: String): Boolean {
        val trimmed = base.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".!?\n"
    }

    companion object {
        private val wordPattern = Regex("[\\p{L}\\p{N}'-]+")

        /** Splits text into lower-case words, keeping apostrophes (don't, o'clock). */
        fun tokenize(text: String): List<String> =
            wordPattern.findAll(text.lowercase())
                .map { it.value.trim('\'', '-') }
                .filter { it.isNotEmpty() }
                .toList()
    }
}
