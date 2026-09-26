package uk.elizabeth.aac.core.predict

/**
 * On-device word prediction: an interpolated trigram model that learns from the messages
 * she actually speaks. Nothing leaves the device. What it has learned can be exported as
 * readable text, and can be erased word by word or completely.
 *
 * A starter vocabulary gives sensible suggestions from day one. Her own usage is weighted
 * more heavily, so her words quickly rise to the top.
 */
class WordPredictor(seedWords: List<String> = emptyList()) {

    private val seed = HashMap<String, Double>()
    private val unigrams = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val trigrams = HashMap<Pair<String, String>, HashMap<String, Int>>()
    private var totalUnigrams = 0

    init {
        // Zipf-like weights: the list is ordered from most to least common.
        seedWords.forEachIndexed { rank, word ->
            val w = word.trim().lowercase()
            if (isLearnable(w) && w !in seed) seed[w] = 1.0 / (rank + 10)
        }
    }

    /** Number of distinct words learned from her. */
    val learnedWordCount: Int get() = unigrams.size

    /** Words learned from her with how often she has used each, most used first. */
    fun learnedWords(): List<Pair<String, Int>> =
        unigrams.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /** Learns from the words of a message that was spoken. */
    fun learn(words: List<String>) {
        val clean = words.map { it.lowercase() }.filter { isLearnable(it) }
        for (i in clean.indices) {
            val w = clean[i]
            unigrams.merge(w, 1, Int::plus)
            totalUnigrams++
            if (i >= 1) bigrams.getOrPut(clean[i - 1]) { HashMap() }.merge(w, 1, Int::plus)
            if (i >= 2) trigrams.getOrPut(clean[i - 2] to clean[i - 1]) { HashMap() }.merge(w, 1, Int::plus)
        }
    }

    /**
     * Up to [count] words starting with [prefix] (which may be empty), most likely first,
     * given the [previous] words of the sentence.
     */
    fun predict(previous: List<String>, prefix: String, count: Int): List<String> {
        val p = prefix.lowercase()
        val prev1 = previous.lastOrNull()?.lowercase()
        val prev2 = previous.getOrNull(previous.size - 2)?.lowercase()

        val scores = HashMap<String, Double>()
        fun addAll(counts: Map<String, Number>, weight: Double, total: Double) {
            if (total <= 0) return
            for ((w, c) in counts) {
                if (w.startsWith(p) && w != p) scores.merge(w, weight * c.toDouble() / total, Double::plus)
            }
        }

        addAll(seed, SEED_WEIGHT, 1.0)
        addAll(unigrams, UNIGRAM_WEIGHT, totalUnigrams.toDouble())
        prev1?.let { bigrams[it] }?.let { addAll(it, BIGRAM_WEIGHT, it.values.sum().toDouble()) }
        if (prev1 != null && prev2 != null) {
            trigrams[prev2 to prev1]?.let { addAll(it, TRIGRAM_WEIGHT, it.values.sum().toDouble()) }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(count)
            .map { it.key }
    }

    /** Stops a word being suggested, and removes everything learned about it. */
    fun forget(word: String) {
        val w = word.lowercase()
        unigrams.remove(w)?.let { totalUnigrams -= it }
        bigrams.remove(w)
        bigrams.values.forEach { it.remove(w) }
        trigrams.keys.removeAll { it.first == w || it.second == w }
        trigrams.values.forEach { it.remove(w) }
        bigrams.values.removeAll { it.isEmpty() }
        trigrams.values.removeAll { it.isEmpty() }
        seed.remove(w)
    }

    /** Erases everything learned from her. The starter vocabulary is kept. */
    fun forgetAll() {
        unigrams.clear()
        bigrams.clear()
        trigrams.clear()
        totalUnigrams = 0
    }

    /** Learned data as readable text, one n-gram per line. Used for storage and data export. */
    fun export(): String = buildString {
        appendLine(HEADER)
        unigrams.toSortedMap().forEach { (w, c) -> appendLine("1\t$w\t$c") }
        bigrams.toSortedMap().forEach { (a, next) ->
            next.toSortedMap().forEach { (b, c) -> appendLine("2\t$a\t$b\t$c") }
        }
        trigrams.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).forEach { (ab, next) ->
            next.toSortedMap().forEach { (w, c) -> appendLine("3\t${ab.first}\t${ab.second}\t$w\t$c") }
        }
    }

    /** Replaces learned data with a previous [export]. Malformed lines are skipped. */
    fun import(data: String) {
        forgetAll()
        for (line in data.lineSequence()) {
            if (line.startsWith("#")) continue
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val count = parts.last().toIntOrNull()?.takeIf { it > 0 } ?: continue
            val words = parts.subList(1, parts.size - 1)
            if (!words.all { isLearnable(it) }) continue
            when (parts[0]) {
                "1" -> if (words.size == 1) {
                    unigrams[words[0]] = count
                    totalUnigrams += count
                }
                "2" -> if (words.size == 2) bigrams.getOrPut(words[0]) { HashMap() }[words[1]] = count
                "3" -> if (words.size == 3) trigrams.getOrPut(words[0] to words[1]) { HashMap() }[words[2]] = count
            }
        }
    }

    private fun isLearnable(word: String) =
        word.isNotEmpty() && word.length <= 40 && word.none { it.isWhitespace() || it.isISOControl() } &&
            word == word.lowercase()

    companion object {
        const val HEADER = "# TheElizabethApp word model v1: order<TAB>words...<TAB>count"
        private const val SEED_WEIGHT = 0.5
        private const val UNIGRAM_WEIGHT = 1.0
        private const val BIGRAM_WEIGHT = 2.0
        private const val TRIGRAM_WEIGHT = 3.0

        /** The bundled British English starter vocabulary, most common first. */
        fun loadSeedWords(): List<String> =
            WordPredictor::class.java.getResourceAsStream("/seed_words_en_gb.txt")
                ?.bufferedReader()
                ?.useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList() }
                ?: emptyList()
    }
}
