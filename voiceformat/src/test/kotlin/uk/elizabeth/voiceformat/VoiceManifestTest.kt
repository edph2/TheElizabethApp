// SPDX-License-Identifier: Apache-2.0
package uk.elizabeth.voiceformat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceManifestTest {
    private val sha = "a".repeat(64)

    @Test
    fun `own voices need consent and stock voices need a licence`() {
        val own = """{"format":"elizabeth-voice-1","engine":"piper-vits","name":"E","kind":"own","modelSha256":"$sha","tokensSha256":"$sha","extra":1}"""
        assertEquals(listOf("This voice has no consent record"), VoiceManifest.parse(own).problems())
        val stock = own.replace("\"own\"", "\"stock\"").replace("\"extra\":1", "\"licence\":\"CC-BY-4.0\"")
        assertEquals(emptyList(), VoiceManifest.parse(stock).problems())
    }

    @Test
    fun `safe paths`() {
        assertTrue(Backup.isSafeRelativePath("voice/espeak-ng-data/voices/!v/Mr serious"))
        listOf("", "/etc/passwd", "a/../b", "a//b", "..", "a\\b", "c:x").forEach {
            assertFalse(Backup.isSafeRelativePath(it), it)
        }
    }

    @Test
    fun `streaming read passes every entry`() {
        val out = ByteArrayOutputStream()
        Backup.write(out, "correct horse".toCharArray()) { w -> w.put("voice/model.onnx", ByteArray(200_000) { 1 }) }
        var size = 0
        Backup.readStreaming(ByteArrayInputStream(out.toByteArray()), "correct horse".toCharArray()) { name, data ->
            assertEquals("voice/model.onnx", name)
            size = data.readBytes().size
        }
        assertEquals(200_000, size)
    }
}
