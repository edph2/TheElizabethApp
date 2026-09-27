package uk.elizabeth.aac

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import uk.elizabeth.aac.data.SecureStore
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Checks the privacy guarantees on a real Android system, not just in the build. */
@RunWith(AndroidJUnit4::class)
class PrivacyGuaranteesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appHasNoNetworkPermissions() {
        for (permission in listOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)) {
            assertEquals(permission, PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
        }
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertFalse(requested.toString(), requested.any { "INTERNET" in it || "NETWORK" in it || "WIFI" in it })
    }

    @Test
    fun appCannotOpenNetworkConnections() {
        // Android refuses to create sockets for apps without the INTERNET permission.
        try {
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 3_000) }
            fail("A network connection was opened")
        } catch (expected: Exception) {
            // SecurityException or SocketException (EACCES/EPERM): exactly what we want.
        }
    }

    @Test
    fun backupIsDisabled() {
        val flags = context.applicationInfo.flags
        assertEquals(0, flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun secureStoreEncryptsWithTheKeystoreAndDetectsTampering() {
        val store = SecureStore(context)
        val secret = "I'm in pain. Please call the doctor.".toByteArray()
        store.write("test-secret.bin", secret)
        assertTrue(secret.contentEquals(store.read("test-secret.bin")))

        val file = File(context.noBackupFilesDir, "secure/test-secret.bin")
        val onDisk = file.readBytes()
        assertFalse("plain text found on disk", String(onDisk, Charsets.ISO_8859_1).contains("doctor"))
        assertNotEquals(secret.size, onDisk.size)

        // A changed byte is detected.
        file.writeBytes(onDisk.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })
        assertThrows { store.read("test-secret.bin") }

        // A file copied under another name is detected (the name is bound into the encryption).
        file.writeBytes(onDisk)
        File(context.noBackupFilesDir, "secure/test-other.bin").writeBytes(onDisk)
        assertThrows { store.read("test-other.bin") }

        store.delete("test-secret.bin")
        store.delete("test-other.bin")
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected an exception")
        } catch (expected: Exception) {
        }
    }
}
