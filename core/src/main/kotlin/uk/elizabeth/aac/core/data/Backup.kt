package uk.elizabeth.aac.core.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPassphraseException : IOException("Wrong passphrase, or the file is damaged")

/** Adds files to an export. */
class BackupWriter internal constructor(private val zip: ZipOutputStream) {
    fun put(name: String, bytes: ByteArray) {
        require(Backup.isSafeRelativePath(name)) { "Bad entry name: $name" }
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    fun put(name: String, text: String) = put(name, text.toByteArray(Charsets.UTF_8))

    /** Adds a file without loading it into memory (for large files such as voice models). */
    fun put(name: String, data: InputStream) {
        require(Backup.isSafeRelativePath(name)) { "Bad entry name: $name" }
        zip.putNextEntry(ZipEntry(name))
        data.copyTo(zip)
        zip.closeEntry()
    }
}

/**
 * Export files: a ZIP of open formats (JSON, text, WAV) encrypted with a passphrase.
 * Used for backups (moving to a new tablet, giving her a copy of her data) and for the
 * voice-training export. The format is documented in docs/DATA_EXPORT.md and can be
 * decrypted without this app using tools/decrypt_export.py.
 *
 * The ZIP is encrypted as a stream of 64 KiB chunks, so large exports never need to fit
 * in memory:
 *
 *   header: "ELIZBAK2" | iterations (int32 BE) | salt (16) | nonce prefix (8)
 *   chunks: final flag (1 byte: 0 or 1) | length (int32 BE) | AES-256-GCM ciphertext + tag
 *
 * key = PBKDF2-HMAC-SHA256(passphrase, salt, iterations); nonce = prefix | chunk index (int32 BE);
 * associated data = "ELIZBAK2" | chunk index | final flag. Reordered, removed or truncated
 * chunks therefore fail to decrypt.
 */
object Backup {
    const val ITERATIONS = 310_000
    const val MIN_PASSPHRASE_LENGTH = 8
    private val MAGIC = "ELIZBAK2".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 64 * 1024
    private const val TAG_BYTES = 16
    private const val MAX_ENTRY_BYTES = 50_000_000
    private const val MAX_TOTAL_BYTES = 4_000_000_000L
    private val entryName = Regex("[A-Za-z0-9._-]{1,80}(/[A-Za-z0-9._-]{1,80})?")

    const val APP_DATA = "appdata.json"
    const val WORDS = "words.txt"
    const val README_NAME = "README.txt"
    const val INFO = "backup-info.json"
    fun recordingEntry(id: String) = "recordings/$id.wav"
    fun voiceBankEntry(id: String) = "voicebank/$id.wav"
    const val VOICES_PREFIX = "voices/"
    fun voiceModelEntry(id: String, relativePath: String) = "$VOICES_PREFIX$id/$relativePath"

    /** A file name that sorts by date, so the newest backup is easy to find. */
    fun suggestedFileName(prefix: String, date: java.time.LocalDate) = "$prefix-$date.elizbak"

    const val README = """This is an export of data from The Elizabeth App.
appdata.json   settings, phrases, message history, voice bank progress and the privacy log (JSON)
words.txt      words the app has learned for prediction (tab-separated text)
recordings/    phrases recorded in her voice (16-bit mono WAV)
voicebank/     voice banking recordings (16-bit mono WAV); the sentence for each is in appdata.json
voices/        installed voice models, if included (Piper ONNX model, phoneme table, pronunciation data)
backup-info.json  when the backup was made and what it contains
"""

    fun isValidEntryName(name: String) = entryName.matches(name) && ".." !in name

    /** Writes an encrypted export and closes [out]. The [passphrase] is not kept. */
    fun write(out: OutputStream, passphrase: CharArray, random: SecureRandom = SecureRandom(), content: (BackupWriter) -> Unit) {
        ZipOutputStream(EncryptingOutputStream(out, passphrase, random)).use { zip -> content(BackupWriter(zip)) }
    }

    /**
     * Reads an encrypted export, calling [onEntry] for each file in it. Throws
     * [WrongPassphraseException] if the passphrase is wrong or the file was altered, and
     * [IOException] if it is incomplete. Because chunks are checked as they are read, callers
     * that change stored data should read the whole file once first to check it.
     */
    fun read(input: InputStream, passphrase: CharArray, onEntry: (name: String, bytes: ByteArray) -> Unit) {
        var total = 0L
        val decrypting = DecryptingInputStream(input, passphrase)
        ZipInputStream(decrypting).use { zip ->
            val buffer = ByteArray(CHUNK)
            while (true) {
                val entry = zip.nextEntry ?: break
                val data = ByteArrayOutputStream()
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    if (data.size() + n > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw IOException("Export is too large")
                    data.write(buffer, 0, n)
                }
                if (!entry.isDirectory && isValidEntryName(entry.name)) onEntry(entry.name, data.toByteArray())
            }
            // Read to the very end so the final chunk is verified and truncation is detected.
            while (decrypting.read(buffer, 0, buffer.size) >= 0) Unit
        }
    }

    /**
     * Reads an encrypted file entry by entry without loading entries into memory, for large
     * files such as voice models. Entry names are passed as they are; callers must check them
     * (see [isSafeRelativePath]). The whole file is read to the end so truncation is detected,
     * but entries seen before a failure may already have been processed.
     */
    fun readStreaming(input: InputStream, passphrase: CharArray, onEntry: (name: String, data: InputStream) -> Unit) {
        val decrypting = DecryptingInputStream(input, passphrase)
        ZipInputStream(decrypting).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) onEntry(entry.name, NonClosingStream(zip))
            }
            val buffer = ByteArray(CHUNK)
            while (decrypting.read(buffer, 0, buffer.size) >= 0) Unit
        }
    }

    /** True for a relative path with no "..", absolute or odd parts, safe to create under a folder. */
    fun isSafeRelativePath(name: String): Boolean {
        if (name.isEmpty() || name.length > 200 || name.startsWith("/") || '\\' in name) return false
        if (name.any { it.isISOControl() || it == ':' }) return false
        return name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
    }

    private class NonClosingStream(private val inner: InputStream) : InputStream() {
        override fun read() = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
        override fun close() = Unit
    }

    internal fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, index: Int) = ByteBuffer.allocate(12).put(prefix).putInt(index).array()

    private fun aad(index: Int, final: Boolean) =
        ByteBuffer.allocate(MAGIC.size + 5).put(MAGIC).putInt(index).put(if (final) 1 else 0).array()

    private class EncryptingOutputStream(
        private val out: OutputStream,
        passphrase: CharArray,
        random: SecureRandom,
    ) : OutputStream() {
        private val prefix = ByteArray(8).also(random::nextBytes)
        private val key: SecretKeySpec
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        private var index = 0
        private var closed = false

        init {
            require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Passphrase is too short" }
            val salt = ByteArray(16).also(random::nextBytes)
            key = deriveKey(passphrase, salt, ITERATIONS)
            out.write(MAGIC)
            out.write(ByteBuffer.allocate(4).putInt(ITERATIONS).array())
            out.write(salt)
            out.write(prefix)
        }

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream closed" }
            var pos = off
            var remaining = len
            while (remaining > 0) {
                if (filled == CHUNK) emit(final = false)
                val n = minOf(remaining, CHUNK - filled)
                System.arraycopy(b, pos, buffer, filled, n)
                filled += n
                pos += n
                remaining -= n
            }
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            emit(final = true)
            closed = true
            out.close()
        }

        private fun emit(final: Boolean) {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce(prefix, index)))
            cipher.updateAAD(aad(index, final))
            val sealed = cipher.doFinal(buffer, 0, filled)
            out.write(if (final) 1 else 0)
            out.write(ByteBuffer.allocate(4).putInt(sealed.size).array())
            out.write(sealed)
            filled = 0
            index++
        }
    }

    private class DecryptingInputStream(input: InputStream, passphrase: CharArray) : InputStream() {
        private val input = DataInputStream(input)
        private val prefix = ByteArray(8)
        private val key: SecretKeySpec
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private var chunk = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var finished = false

        init {
            val magic = ByteArray(MAGIC.size)
            try {
                this.input.readFully(magic)
            } catch (e: EOFException) {
                throw IllegalArgumentException("This is not an Elizabeth App export file")
            }
            require(magic.contentEquals(MAGIC)) { "This is not an Elizabeth App export file" }
            val iterations = this.input.readInt()
            require(iterations in 10_000..10_000_000) { "Unsupported export file" }
            val salt = ByteArray(16).also { this.input.readFully(it) }
            this.input.readFully(prefix)
            key = deriveKey(passphrase, salt, iterations)
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos == chunk.size) {
                if (finished) return -1
                nextChunk()
            }
            val n = minOf(len, chunk.size - pos)
            System.arraycopy(chunk, pos, b, off, n)
            pos += n
            return n
        }

        private fun nextChunk() {
            val flag = input.read()
            if (flag < 0) throw IOException("The export file is incomplete")
            if (flag > 1) throw WrongPassphraseException()
            val final = flag == 1
            val length = try { input.readInt() } catch (e: EOFException) { throw IOException("The export file is incomplete") }
            if (length < TAG_BYTES || length > CHUNK + TAG_BYTES) throw WrongPassphraseException()
            val sealed = ByteArray(length)
            try { input.readFully(sealed) } catch (e: EOFException) { throw IOException("The export file is incomplete") }
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce(prefix, index)))
            cipher.updateAAD(aad(index, final))
            chunk = try { cipher.doFinal(sealed) } catch (e: AEADBadTagException) { throw WrongPassphraseException() }
            pos = 0
            index++
            if (final) {
                if (input.read() != -1) throw IOException("Unexpected data after the end of the export")
                finished = true
            }
        }

        override fun close() = input.close()
    }
}
