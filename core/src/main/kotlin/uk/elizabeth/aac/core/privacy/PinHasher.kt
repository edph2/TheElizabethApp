package uk.elizabeth.aac.core.privacy

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores the carer PIN as a salted PBKDF2 hash, never in plain text. */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun hash(pin: String, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val derived = derive(pin, salt, ITERATIONS)
        val b64 = Base64.getEncoder()
        return "pbkdf2-sha256:$ITERATIONS:${b64.encodeToString(salt)}:${b64.encodeToString(derived)}"
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { b64.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt, iterations))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
