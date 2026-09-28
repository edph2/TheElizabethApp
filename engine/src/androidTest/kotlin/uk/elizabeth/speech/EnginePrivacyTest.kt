// SPDX-License-Identifier: GPL-3.0-or-later
// Piper Voice Engine. Free software under the GNU GPL v3 or later: see engine/LICENSE.
package uk.elizabeth.speech

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket

/** The speech engine's privacy guarantees, checked on a real Android system. */
@RunWith(AndroidJUnit4::class)
class EnginePrivacyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun engineHasNoNetworkAccess() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertFalse(requested.toString(), requested.any { "INTERNET" in it || "NETWORK" in it || "WIFI" in it })
        try {
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 3_000) }
            fail("A network connection was opened")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun backupIsDisabled() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
