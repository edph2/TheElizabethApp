// SPDX-License-Identifier: Apache-2.0
package uk.elizabeth.voiceformat

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
