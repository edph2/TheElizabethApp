package uk.elizabeth.aac.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted file storage. Every file is sealed with AES-256-GCM using a key that lives in the
 * Android Keystore (in secure hardware where the tablet has it) and never leaves it.
 *
 * Files live in noBackupFilesDir, which Android never backs up. Each file is:
 * version (1 byte) | IV (12 bytes) | ciphertext + GCM tag. The file name is bound in as
 * associated data, so files cannot be swapped for one another.
 */
class SecureStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "secure").apply { mkdirs() }

    fun write(name: String, plain: ByteArray) {
        checkName(name)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray())
        val sealed = cipher.doFinal(plain)
        val iv = cipher.iv
        require(iv.size == IV_BYTES)
        val bytes = ByteBuffer.allocate(1 + IV_BYTES + sealed.size).put(VERSION).put(iv).put(sealed).array()
        // Write to a temporary file then rename, so a crash never leaves a half-written file.
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(dir, name))) {
            tmp.delete()
            throw java.io.IOException("Could not save $name")
        }
    }

    /** The decrypted file, or null if it does not exist. */
    fun read(name: String): ByteArray? {
        checkName(name)
        val file = File(dir, name)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        require(bytes.size > 1 + IV_BYTES && bytes[0] == VERSION) { "Unrecognised file format: $name" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, IV_BYTES))
        cipher.updateAAD(name.toByteArray())
        return cipher.doFinal(bytes, 1 + IV_BYTES, bytes.size - 1 - IV_BYTES)
    }

    fun delete(name: String) {
        checkName(name)
        File(dir, name).delete()
    }

    /** Moves an unreadable file aside so it is not overwritten, in case it can be recovered later. */
    fun quarantine(name: String) {
        checkName(name)
        File(dir, name).renameTo(File(dir, "$name.unreadable-${System.currentTimeMillis()}"))
    }

    fun list(prefix: String): List<String> =
        dir.listFiles()?.map { it.name }?.filter { it.startsWith(prefix) && !it.endsWith(".tmp") } ?: emptyList()

    /** Deletes every file and the key itself, so nothing that was stored can be recovered. */
    fun destroyAll() {
        dir.listFiles()?.forEach { it.delete() }
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun checkName(name: String) {
        require(name.matches(Regex("[A-Za-z0-9._-]{1,100}")) && !name.startsWith(".")) { "Bad file name" }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "elizabeth-data-key-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val VERSION: Byte = 1
    }
}
