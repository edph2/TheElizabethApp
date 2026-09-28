package uk.elizabeth.aac.core.voicebank

import kotlinx.serialization.Serializable
import uk.elizabeth.voiceformat.VoiceConsent

/** One kept recording of one script sentence. */
@Serializable
data class VoiceTake(
    val promptIndex: Int,
    val recordingId: String,
    val durationMs: Long,
    /** False if the quality check found a problem but the take was kept anyway. */
    val good: Boolean,
    val timeMillis: Long,
)

/** Progress through the voice banking script. The audio itself is stored separately, encrypted. */
@Serializable
data class VoiceBank(
    val consent: VoiceConsent? = null,
    val takes: List<VoiceTake> = emptyList(),
    /** Extra sentences added by the family: her own sayings, names, places. */
    val extraPrompts: List<String> = emptyList(),
) {
    val totalMs: Long get() = takes.sumOf { it.durationMs }

    fun prompts(script: List<String>): List<String> = script + extraPrompts

    fun takeFor(promptIndex: Int): VoiceTake? = takes.firstOrNull { it.promptIndex == promptIndex }

    /** Adds a take, replacing any earlier take of the same sentence (whose recording should then be deleted). */
    fun withTake(take: VoiceTake): VoiceBank =
        copy(takes = takes.filterNot { it.promptIndex == take.promptIndex } + take)

    fun withoutTake(promptIndex: Int): VoiceBank = copy(takes = takes.filterNot { it.promptIndex == promptIndex })

    /** The next sentence without a recording, searching forward from [from] and wrapping round. */
    fun nextUnrecorded(from: Int, promptCount: Int): Int? {
        if (promptCount <= 0) return null
        val recorded = takes.map { it.promptIndex }.toSet()
        for (step in 0 until promptCount) {
            val i = (from + step).mod(promptCount)
            if (i !in recorded) return i
        }
        return null
    }

    /**
     * True if a voice was made from recordings given under this bank's consent (same speaker,
     * same moment of consent). Used to remove such voices when consent is withdrawn.
     */
    fun isSourceOf(voiceConsent: VoiceConsent?): Boolean {
        val mine = consent ?: return false
        return voiceConsent != null && voiceConsent.timeMillis == mine.timeMillis && voiceConsent.speakerName == mine.speakerName
    }

    fun addPrompt(text: String): VoiceBank {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        return if (clean.isEmpty()) this else copy(extraPrompts = extraPrompts + clean)
    }

    companion object {
        /** The bundled British English script, about 300 sentences. */
        fun loadScript(): List<String> =
            VoiceBank::class.java.getResourceAsStream("/voice_bank_script_en_gb.txt")
                ?.bufferedReader()
                ?.useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList() }
                ?: emptyList()

        /** Roughly how much recording gives a usable voice, and a good one. */
        const val USABLE_MS = 20 * 60 * 1000L
        const val GOOD_MS = 60 * 60 * 1000L
    }
}
