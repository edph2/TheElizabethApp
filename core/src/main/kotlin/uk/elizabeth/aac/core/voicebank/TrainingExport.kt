package uk.elizabeth.aac.core.voicebank

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.elizabeth.aac.core.audio.Wav
import uk.elizabeth.voiceformat.BackupWriter
import uk.elizabeth.voiceformat.VoiceConsent
import uk.elizabeth.voiceformat.sha256Hex

@Serializable
data class TrainingManifest(
    val format: String = "ljspeech",
    val language: String = "en-GB",
    val sampleRate: Int = Wav.DEFAULT_SAMPLE_RATE,
    val speakerName: String,
    val consent: VoiceConsent,
    val utterances: Int,
    val totalSeconds: Long,
    val createdAtMillis: Long,
    /** SHA-256 of every WAV file, so the training data can be checked and traced. */
    val files: Map<String, String>,
)

/**
 * Writes voice banking recordings in the LJSpeech layout used by Piper and most open-source
 * TTS trainers:
 *
 *   wavs/<id>.wav      16-bit mono PCM, 22.05 kHz
 *   metadata.csv       <id>|<text>|<text>
 *   manifest.json      speaker, consent, checksums
 *   README.txt         what to do next (tools/voice-training/README.md)
 */
object TrainingExport {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    const val README = """Voice banking recordings from The Elizabeth App, ready for training a voice model.
Format: LJSpeech (wavs/ + metadata.csv). manifest.json records whose voice this is, their consent, and a
SHA-256 checksum of every recording. Train only on a computer you trust, following
tools/voice-training/README.md in the app's source code, and delete these files afterwards.
"""

    /**
     * Writes every take that has audio. [audioFor] returns the WAV bytes of a take, or null if
     * it cannot be read (it is then skipped). Returns the number of utterances written.
     */
    fun write(
        writer: BackupWriter,
        bank: VoiceBank,
        prompts: List<String>,
        nowMillis: Long,
        audioFor: (VoiceTake) -> ByteArray?,
    ): Int {
        val consent = requireNotNull(bank.consent) { "Consent must be recorded before exporting a voice" }
        val metadata = StringBuilder()
        val checksums = LinkedHashMap<String, String>()
        var totalMs = 0L
        for (take in bank.takes.sortedBy { it.promptIndex }) {
            val text = prompts.getOrNull(take.promptIndex) ?: continue
            val wav = audioFor(take) ?: continue
            val id = "utt%05d".format(take.promptIndex + 1)
            writer.put("wavs/$id.wav", wav)
            val clean = text.replace('|', ' ').replace('\n', ' ').trim()
            metadata.append(id).append('|').append(clean).append('|').append(clean).append('\n')
            checksums["wavs/$id.wav"] = sha256Hex(wav)
            totalMs += take.durationMs
        }
        writer.put("metadata.csv", metadata.toString())
        val manifest = TrainingManifest(
            speakerName = consent.speakerName,
            consent = consent,
            utterances = checksums.size,
            totalSeconds = totalMs / 1000,
            createdAtMillis = nowMillis,
            files = checksums,
        )
        writer.put("manifest.json", json.encodeToString(TrainingManifest.serializer(), manifest))
        writer.put("README.txt", README)
        return checksums.size
    }
}
