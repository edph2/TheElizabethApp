package uk.elizabeth.aac.core.text

/**
 * Splits a message into sentences so speech can start after the first sentence is ready,
 * instead of after the whole message. Very long sentences are split at commas or spaces.
 */
object Sentences {
    private val boundary = Regex("(?<=[.!?])\\s+|\\n+")

    fun split(text: String, maxLength: Int = 200): List<String> =
        text.split(boundary).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { chop(it, maxLength) }

    private fun chop(sentence: String, maxLength: Int): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        val parts = mutableListOf<String>()
        var rest = sentence
        while (rest.length > maxLength) {
            val window = rest.substring(0, maxLength)
            val cut = window.lastIndexOf(", ").takeIf { it > maxLength / 3 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: maxLength
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
