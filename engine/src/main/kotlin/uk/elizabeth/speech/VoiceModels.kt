// SPDX-License-Identifier: GPL-3.0-or-later
// Piper Voice Engine. Free software under the GNU GPL v3 or later: see engine/LICENSE.
package uk.elizabeth.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import uk.elizabeth.voiceformat.Backup
import uk.elizabeth.voiceformat.sha256Hex
import uk.elizabeth.voiceformat.VoiceManifest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
 * The engine reads model files directly from disk, so installed models are stored in this
 * app's private no-backup folder, protected by Android's file-based encryption and the app
 * sandbox. Every voice is checked before it is installed: a consent record (or, for a
 * published voice, a licence) and the checksums of the model and phoneme table.
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

    /**
     * Writes an installed voice as a plain (unencrypted) ZIP in the voice package layout
     * ("voice/..."), including the shared pronunciation data. Used by apps that back voices up
     * inside their own encrypted backups.
     */
    fun exportPlain(voice: InstalledVoice, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            for ((path, file) in files(voice)) {
                zip.putNextEntry(ZipEntry("voice/$path"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Installs a voice from a plain ZIP written by [exportPlain], keeping [id]. An installed voice
     * with the same id is replaced only once the new copy has passed every check.
     */
    fun installPlain(id: String, input: InputStream): InstalledVoice {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Bad voice id" }
        val partial = partialDir(id)
        try {
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory || !name.startsWith("voice/") || !Backup.isSafeRelativePath(name)) continue
                    total += writeInto(partial, name.removePrefix("voice/"), zip, MAX_BYTES - total)
                }
            }
            val existing = File(root, id)
            val previous = File(root, id + OLD)
            if (existing.exists()) existing.renameTo(previous)
            try {
                val installed = install(partial, id)
                previous.deleteRecursively()
                return installed
            } catch (e: Exception) {
                if (previous.exists()) previous.renameTo(existing)
                throw e
            }
        } finally {
            if (partial.exists()) partial.deleteRecursively()
        }
    }

    /** Removes any half-installed voices (e.g. after the app was stopped mid-import). */
    fun discardPartial() {
        root.listFiles { f -> f.name.endsWith(PARTIAL) || f.name.endsWith(OLD) }.orEmpty().forEach { it.deleteRecursively() }
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
 * Synthesises speech with an installed Piper voice using sherpa-onnx, entirely on the device.
 * Loading takes a second or two, so keep one instance while the voice is in use.
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

    val sampleRate: Int get() = tts.sampleRate()

    /**
     * Synthesises [text] sentence by sentence, passing each piece of audio (16-bit PCM) to
     * [onAudio] as soon as it is ready. [onAudio] returns false to stop. [speed] 1.0 is normal.
     */
    fun stream(text: String, speed: Float, onAudio: (ShortArray) -> Boolean) {
        tts.generateWithCallback(text, 0, speed.coerceIn(0.5f, 2.0f)) { samples ->
            if (onAudio(toPcm(samples))) 1 else 0
        }
    }

    /** Synthesises [text] in one go. Call off the main thread. */
    fun synthesize(text: String, speed: Float): ShortArray =
        toPcm(tts.generate(text, 0, speed.coerceIn(0.5f, 2.0f)).samples)

    fun release() = tts.release()

    private fun toPcm(samples: FloatArray) = ShortArray(samples.size) { i ->
        (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    }
}
