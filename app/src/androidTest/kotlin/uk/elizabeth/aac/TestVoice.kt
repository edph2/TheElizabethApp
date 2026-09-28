package uk.elizabeth.aac

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import uk.elizabeth.aac.speech.EngineClient
import uk.elizabeth.voiceformat.Backup
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Installs the published test voice (packaged by CI) into the Piper Voice Engine app. */
object TestVoice {
    const val ID = "test-voice"

    fun available(): Boolean =
        InstrumentationRegistry.getInstrumentation().context.assets.list("")?.contains("test.elizvoice") == true

    fun install(context: Context, engine: EngineClient) {
        // The test asset is an encrypted .elizvoice; the engine's provider takes a plain voice ZIP.
        val plain = File(context.cacheDir, "test-voice.zip")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("test.elizvoice").use { input ->
                ZipOutputStream(plain.outputStream()).use { zip ->
                    Backup.readStreaming(input, "correct horse battery".toCharArray()) { name, data ->
                        zip.putNextEntry(ZipEntry(name))
                        data.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }
            plain.inputStream().use { engine.installVoice(ID, it) }
        } finally {
            plain.delete()
        }
    }
}
