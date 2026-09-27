package uk.elizabeth.aac.core.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.elizabeth.aac.core.voicebank.VoiceConsent

/**
 * Describes a voice model packaged by tools/voice-training/package_voice.py (".elizvoice").
 * Package layout (inside the encrypted ZIP):
 *
 *   voice/manifest.json      this manifest
 *   voice/model.onnx         Piper (VITS) model with sherpa-onnx metadata
 *   voice/tokens.txt         phoneme symbol table
 *   voice/espeak-ng-data/... pronunciation data used to turn text into phonemes
 */
@Serializable
data class VoiceManifest(
    val format: String,
    val engine: String,
    val name: String,
    /** "own" (her voice), "donor" (someone else's, with consent) or "stock" (a published open voice). */
    val kind: String = "own",
    val speakerName: String? = null,
    val consent: VoiceConsent? = null,
    val licence: String? = null,
    val baseModel: String? = null,
    val sampleRate: Int = 22050,
    val createdAtMillis: Long = 0,
    val modelSha256: String,
    val tokensSha256: String,
    val syntheticVoiceNotice: String = "This is an AI-generated voice.",
) {
    /** Problems that mean this voice must not be used. */
    fun problems(): List<String> = buildList {
        if (format != FORMAT) add("Unsupported voice format: $format")
        if (engine != ENGINE) add("Unsupported voice engine: $engine")
        if (kind != "stock" && consent == null) add("This voice has no consent record")
        if (kind == "stock" && licence.isNullOrBlank()) add("This published voice has no licence information")
        if (!sha.matches(modelSha256) || !sha.matches(tokensSha256)) add("The voice file's checksums are malformed")
    }

    companion object {
        const val FORMAT = "elizabeth-voice-1"
        const val ENGINE = "piper-vits"
        const val MANIFEST = "voice/manifest.json"
        const val MODEL = "voice/model.onnx"
        const val TOKENS = "voice/tokens.txt"
        const val ESPEAK_PREFIX = "voice/espeak-ng-data/"
        private val sha = Regex("[0-9a-f]{64}")
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): VoiceManifest = json.decodeFromString(serializer(), text)
    }
}
