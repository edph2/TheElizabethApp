package uk.elizabeth.aac.core.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

/** Everything needed to move her data to a new tablet, or to give her a copy of it. */
data class BackupContents(
    val appDataJson: String,
    val wordModel: String,
    /** Recording id to WAV bytes. */
    val recordings: Map<String, ByteArray>,
)

class WrongPassphraseException : Exception("Wrong passphrase, or the file is damaged")

/**
 * The export file: a ZIP of open formats (JSON, text, WAV), encrypted with AES-256-GCM using
 * a key derived from a passphrase (PBKDF2-HMAC-SHA256). The format is documented in
 * docs/DATA_EXPORT.md and can be decrypted without this app using tools/decrypt_export.py.
 *
 * Layout: "ELIZBAK1" | iterations (int32 BE) | salt (16 bytes) | IV (12 bytes) | ciphertext + tag
 */
object Backup {
    private val MAGIC = "ELIZBAK1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 310_000
    const val MIN_PASSPHRASE_LENGTH = 8
    private const val MAX_UNZIPPED_BYTES = 1_000_000_000L
    private val recordingIdPattern = Regex("[A-Za-z0-9-]{1,64}")

    const val README = """This is an export of data from The Elizabeth App.
appdata.json   settings, phrases, message history and the privacy log (JSON)
words.txt      words the app has learned for prediction (tab-separated text)
recordings/    her recorded phrases (16-bit mono WAV)
"""

    fun zip(contents: BackupContents): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("README.txt", README.toByteArray())
            put("appdata.json", contents.appDataJson.toByteArray())
            put("words.txt", contents.wordModel.toByteArray())
            contents.recordings.toSortedMap().forEach { (id, wav) ->
                require(recordingIdPattern.matches(id)) { "Bad recording id" }
                put("recordings/$id.wav", wav)
            }
        }
        return out.toByteArray()
    }

    fun unzip(bytes: ByteArray): BackupContents {
        var appData: String? = null
        var words = ""
        val recordings = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val data = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_UNZIPPED_BYTES) throw IllegalArgumentException("Export is too large")
                    data.write(buffer, 0, n)
                }
                val name = entry.name
                when {
                    name == "appdata.json" -> appData = data.toString(Charsets.UTF_8.name())
                    name == "words.txt" -> words = data.toString(Charsets.UTF_8.name())
                    name.startsWith("recordings/") && name.endsWith(".wav") -> {
                        val id = name.removePrefix("recordings/").removeSuffix(".wav")
                        if (recordingIdPattern.matches(id)) recordings[id] = data.toByteArray()
                    }
                }
            }
        }
        return BackupContents(appData ?: throw IllegalArgumentException("Not an export: appdata.json missing"), words, recordings)
    }

    fun encrypt(plain: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Passphrase is too short" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + 4 + salt.size + iv.size + sealed.size)
            .put(MAGIC).putInt(ITERATIONS).put(salt).put(iv).put(sealed).array()
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        val header = MAGIC.size + 4 + 16 + 12
        if (data.size < header + 16 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IllegalArgumentException("This is not an Elizabeth App export file")
        }
        val buffer = ByteBuffer.wrap(data)
        buffer.position(MAGIC.size)
        val iterations = buffer.int
        require(iterations in 10_000..10_000_000) { "Unsupported export file" }
        val salt = ByteArray(16).also { buffer.get(it) }
        val iv = ByteArray(12).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(data, header, data.size - header)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
