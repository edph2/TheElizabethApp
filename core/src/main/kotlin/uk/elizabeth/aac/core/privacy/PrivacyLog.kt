package uk.elizabeth.aac.core.privacy

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
enum class PrivacyEventType {
    APP_FIRST_RUN,
    LEARNING_ENABLED,
    LEARNING_DISABLED,
    HISTORY_ENABLED,
    HISTORY_DISABLED,
    WORD_FORGOTTEN,
    LEARNED_WORDS_ERASED,
    HISTORY_ERASED,
    RECORDING_ADDED,
    RECORDING_DELETED,
    DATA_EXPORTED,
    DATA_RESTORED,
    ALL_DATA_ERASED,
    VOICE_CONSENT_RECORDED,
    VOICE_CONSENT_WITHDRAWN,
    VOICE_TRAINING_DATA_EXPORTED,
    VOICE_MODEL_ADDED,
    VOICE_MODEL_REMOVED,
    CARER_PIN_SET,
    CARER_PIN_REMOVED,
}

@Serializable
data class PrivacyEvent(
    val sequence: Long,
    val timeMillis: Long,
    val type: PrivacyEventType,
    val detail: String,
    val previousHash: String,
    val hash: String,
)

/**
 * A local, tamper-evident record of privacy-relevant events (export, erase, learning on/off...).
 * Each entry contains the SHA-256 hash of the entry before it, so editing or deleting an
 * entry in the middle breaks the chain and is detected by [verify].
 *
 * The log never contains message content, only what kind of event happened and when.
 */
@Serializable
data class PrivacyLog(val events: List<PrivacyEvent> = emptyList()) {

    fun append(type: PrivacyEventType, timeMillis: Long, detail: String = ""): PrivacyLog {
        val previous = events.lastOrNull()
        val sequence = (previous?.sequence ?: 0) + 1
        val previousHash = previous?.hash ?: GENESIS
        val hash = hashOf(sequence, timeMillis, type, detail, previousHash)
        return PrivacyLog(events + PrivacyEvent(sequence, timeMillis, type, detail, previousHash, hash))
    }

    /** True if every entry is intact and correctly chained to the one before. */
    fun verify(): Boolean {
        var previousHash = GENESIS
        var expectedSequence = 1L
        for (e in events) {
            if (e.sequence != expectedSequence || e.previousHash != previousHash) return false
            if (e.hash != hashOf(e.sequence, e.timeMillis, e.type, e.detail, e.previousHash)) return false
            previousHash = e.hash
            expectedSequence++
        }
        return true
    }

    companion object {
        const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

        private fun hashOf(sequence: Long, timeMillis: Long, type: PrivacyEventType, detail: String, previousHash: String): String {
            val canonical = listOf(sequence, timeMillis, type.name, detail, previousHash).joinToString("\u001f")
            return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
        }
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
