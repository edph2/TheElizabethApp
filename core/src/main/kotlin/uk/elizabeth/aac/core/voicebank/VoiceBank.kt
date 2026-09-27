package uk.elizabeth.aac.core.voicebank

import kotlinx.serialization.Serializable

/**
 * Consent to record a voice and turn it into a synthetic voice. Recorded before any voice
 * banking starts, for her own voice or for a donor's. Kept with the recordings and exported
 * with them, so any voice model made from them can be traced back to this consent.
 */
@Serializable
data class VoiceConsent(
    val speakerName: String,
    /** True if the speaker is the person who will use the voice. */
    val isSelf: Boolean,
    val statement: String,
    val timeMillis: Long,
) {
    companion object {
        fun statementFor(speakerName: String, isSelf: Boolean): String =
            if (isSelf) {
                "I, $speakerName, agree to record my voice so that a synthetic voice can be made for me to speak with. " +
                    "The recordings and the voice stay under my control and will only be used for my own communication. " +
                    "I can withdraw this consent and delete the recordings at any time."
            } else {
                "I, $speakerName, agree to record my voice so that a synthetic voice can be made for the user of this tablet " +
                    "to speak with. It will only be used for her own communication. I can withdraw this consent and ask for the " +
                    "recordings and the voice to be deleted at any time."
            }
    }
}

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
