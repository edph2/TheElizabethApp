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

/** A voice model installed on the tablet. [espeakData] is shared by all voices. */
class InstalledVoice(val id: String, val dir: File, val manifest: VoiceManifest, val espeakData: File) {
    val model get() = File(dir, "model.onnx")
    val tokens get() = File(dir, "tokens.txt")
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

    /**
     * Pronunciation data (espeak-ng) shared by every voice, at a path that never changes. The
     * speech engine loads it once per app session from the first voice used, so it must not
     * live inside a voice's folder, where removing that voice would pull it from under the engine.
     */
    private val sharedEspeak = File(root, ESPEAK)

    fun list(): List<InstalledVoice> =
        root.listFiles { f -> f.isDirectory && f != sharedEspeak && !f.name.endsWith(PARTIAL) && !f.name.endsWith(OLD) }.orEmpty()
            .mapNotNull { dir -> runCatching { load(dir) }.getOrNull() }
            .sortedBy { it.manifest.name }

    fun find(id: String): InstalledVoice? = list().firstOrNull { it.id == id }

    /**
     * Decrypts, unpacks and checks a voice package. Nothing is installed unless every check
     * passes: consent or licence present, and the model and tokens match their checksums.
     */
    fun import(input: InputStream, passphrase: CharArray): InstalledVoice {
        val id = UUID.randomUUID().toString()
        val partial = partialDir(id)
        try {
            var total = 0L
            Backup.readStreaming(input, passphrase) { name, data ->
                if (!name.startsWith("voice/") || !Backup.isSafeRelativePath(name)) return@readStreaming
                total += writeInto(partial, name.removePrefix("voice/"), data, MAX_BYTES - total)
            }
            return install(partial, id)
        } finally {
            if (partial.exists()) partial.deleteRecursively()
        }
    }

    // ---- Backup and restore of installed voices ----

    /**
     * Every file of an installed voice, as (path within the voice package, file), for backups.
     * Includes the shared pronunciation data, so each voice in a backup is complete on its own.
     */
    fun files(voice: InstalledVoice): List<Pair<String, File>> {
        fun under(dir: File, prefix: String) = dir.walkTopDown().filter { it.isFile }
            .map { prefix + it.relativeTo(dir).invariantSeparatorsPath to it }
        return (under(voice.dir, "") + under(sharedEspeak, "$ESPEAK/")).sortedBy { it.first }.toList()
    }

    /** Writes one file of a voice being restored from a backup. Call [finishRestore] afterwards. */
    fun restoreFile(id: String, relativePath: String, data: InputStream) {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Bad voice id in backup" }
        writeInto(partialDir(id), relativePath, data, MAX_BYTES)
    }

    /**
     * Installs the voices written by [restoreFile], replacing any installed voice with the same
     * id. Each voice is checked exactly as on import. Returns the names of voices that failed.
     */
    fun finishRestore(): List<String> {
        val failed = mutableListOf<String>()
        root.listFiles { f -> f.isDirectory && f.name.endsWith(PARTIAL) }.orEmpty().forEach { partial ->
            val id = partial.name.removeSuffix(PARTIAL)
            // Keep the installed copy until the restored one has passed its checks.
            val existing = File(root, id)
            val previous = File(root, id + OLD)
            if (existing.exists()) existing.renameTo(previous)
            try {
                install(partial, id)
                previous.deleteRecursively()
            } catch (e: Exception) {
                failed += runCatching { load(partial).manifest.name }.getOrDefault(id)
                if (previous.exists()) previous.renameTo(existing)
            } finally {
                if (partial.exists()) partial.deleteRecursively()
            }
        }
        return failed
    }

    /** Removes any half-restored voices (e.g. after a failed restore). */
    fun discardPartial() {
        root.listFiles { f -> f.name.endsWith(PARTIAL) }.orEmpty().forEach { it.deleteRecursively() }
    }

    private fun partialDir(id: String) = File(root, id + PARTIAL).apply { mkdirs() }

    /** Writes [data] to [relativePath] under [dir], refusing unsafe paths and anything over [limit] bytes. */
    private fun writeInto(dir: File, relativePath: String, data: InputStream, limit: Long): Long {
        if (!Backup.isSafeRelativePath(relativePath)) throw IOException("Unsafe file name in voice")
        val target = File(dir, relativePath)
        if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) throw IOException("Unsafe file name in voice")
        target.parentFile?.mkdirs()
        var written = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = data.read(buffer)
                if (n < 0) break
                written += n
                if (written > limit) throw IOException("Voice is too large")
                out.write(buffer, 0, n)
            }
        }
        return written
    }

    /** Checks a complete voice folder and moves it into place. */
    private fun install(partial: File, id: String): InstalledVoice {
        val voice = load(partial)
        val packagedEspeak = File(partial, ESPEAK)
        val problems = voice.manifest.problems().toMutableList()
        if (!packagedEspeak.isDirectory && !sharedEspeak.isDirectory) problems += "The voice has no pronunciation data"
        if (fileSha256(voice.model) != voice.manifest.modelSha256) problems += "The voice model does not match its checksum"
        if (sha256Hex(voice.tokens.readBytes()) != voice.manifest.tokensSha256) problems += "The voice's phoneme table does not match its checksum"
        if (problems.isNotEmpty()) throw IOException(problems.joinToString(". "))
        // The first voice provides the shared pronunciation data; later copies are not needed.
        if (packagedEspeak.isDirectory) {
            if (!sharedEspeak.exists()) packagedEspeak.renameTo(sharedEspeak) else packagedEspeak.deleteRecursively()
        }
        val installed = File(root, id)
        if (!partial.renameTo(installed)) throw IOException("Could not install the voice")
        return load(installed)
    }

    fun delete(id: String) {
        File(root, id).takeIf { it.parentFile == root }?.deleteRecursively()
    }

    fun deleteAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun load(dir: File): InstalledVoice {
        val manifest = VoiceManifest.parse(File(dir, "manifest.json").readText())
        return InstalledVoice(dir.name.removeSuffix(PARTIAL), dir, manifest, sharedEspeak)
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
        const val OLD = ".old"
        const val ESPEAK = "espeak-ng-data"
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
