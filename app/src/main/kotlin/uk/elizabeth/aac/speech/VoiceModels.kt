package uk.elizabeth.aac.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import uk.elizabeth.aac.core.audio.PcmAudio
import uk.elizabeth.aac.core.data.Backup
import uk.elizabeth.aac.core.privacy.sha256Hex
import uk.elizabeth.aac.core.voice.VoiceManifest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/** A voice model installed on the tablet. */
class InstalledVoice(val id: String, val dir: File, val manifest: VoiceManifest) {
    val model get() = File(dir, "model.onnx")
    val tokens get() = File(dir, "tokens.txt")
    val espeakData get() = File(dir, "espeak-ng-data")
}

/**
 * Installs and removes voice models (".elizvoice" files from tools/voice-training).
 *
 * The engine reads model files directly from disk, so installed models are stored in the
 * app's private no-backup folder, protected by Android's file-based encryption and the app
 * sandbox, rather than by the app's own encryption. Her raw recordings, which are more
 * sensitive, stay encrypted by the app.
 */
class VoiceModels(context: Context) {
    private val root = File(context.noBackupFilesDir, "voices").apply { mkdirs() }

    fun list(): List<InstalledVoice> =
        root.listFiles { f -> f.isDirectory && !f.name.endsWith(PARTIAL) }.orEmpty()
            .mapNotNull { dir -> runCatching { load(dir) }.getOrNull() }
            .sortedBy { it.manifest.name }

    fun find(id: String): InstalledVoice? = list().firstOrNull { it.id == id }

    /**
     * Decrypts, unpacks and checks a voice package. Nothing is installed unless every check
     * passes: consent or licence present, and the model and tokens match their checksums.
     */
    fun import(input: InputStream, passphrase: CharArray): InstalledVoice {
        val id = UUID.randomUUID().toString()
        val partial = File(root, id + PARTIAL)
        partial.mkdirs()
        try {
            var total = 0L
            val base = partial.canonicalPath + File.separator
            Backup.readStreaming(input, passphrase) { name, data ->
                if (!name.startsWith("voice/") || !Backup.isSafeRelativePath(name)) return@readStreaming
                val target = File(partial, name.removePrefix("voice/"))
                if (!target.canonicalPath.startsWith(base)) throw IOException("Unsafe file name in voice package")
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = data.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Voice package is too large")
                        out.write(buffer, 0, n)
                    }
                }
            }
            val voice = load(partial)
            val problems = voice.manifest.problems().toMutableList()
            if (!voice.espeakData.isDirectory) problems += "The voice package has no pronunciation data"
            if (fileSha256(voice.model) != voice.manifest.modelSha256) problems += "The voice model does not match its checksum"
            if (sha256Hex(voice.tokens.readBytes()) != voice.manifest.tokensSha256) problems += "The voice's phoneme table does not match its checksum"
            if (problems.isNotEmpty()) throw IOException(problems.joinToString(". "))
            val installed = File(root, id)
            if (!partial.renameTo(installed)) throw IOException("Could not install the voice")
            return load(installed)
        } finally {
            if (partial.exists()) partial.deleteRecursively()
        }
    }

    fun delete(id: String) {
        File(root, id).takeIf { it.parentFile == root }?.deleteRecursively()
    }

    fun deleteAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun load(dir: File): InstalledVoice {
        val manifest = VoiceManifest.parse(File(dir, "manifest.json").readText())
        return InstalledVoice(dir.name.removeSuffix(PARTIAL), dir, manifest)
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PARTIAL = ".partial"
        const val MAX_BYTES = 400L * 1024 * 1024
    }
}

/**
 * Speaks with an installed Piper voice, entirely on the tablet, using the sherpa-onnx engine.
 * Loading takes a second or two, so keep one instance while the voice is selected.
 */
class PiperVoice(val voice: InstalledVoice) {
    private val tts = OfflineTts(
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = voice.model.absolutePath,
                    tokens = voice.tokens.absolutePath,
                    dataDir = voice.espeakData.absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        ),
    )

    /** Synthesises [text]. [speed] 1.0 is normal. Call off the main thread. */
    fun synthesize(text: String, speed: Float): PcmAudio {
        val audio = tts.generate(text, 0, speed.coerceIn(0.5f, 2.0f))
        val samples = ShortArray(audio.samples.size) { i ->
            (audio.samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return PcmAudio(samples, audio.sampleRate)
    }

    fun release() = tts.release()
}
