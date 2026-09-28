package uk.elizabeth.aac.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.IOException
import java.io.InputStream

/** A voice installed in the Piper Voice Engine app, as described by its manifest. */
data class EngineVoice(
    val id: String,
    val name: String,
    val kind: String,
    val speakerName: String?,
    val licence: String?,
    val locale: String,
    val consentSpeaker: String?,
    val consentTimeMillis: Long?,
    val notice: String,
)

/**
 * Talks to the separate Piper Voice Engine app. Speech goes through Android's standard
 * TextToSpeech API (see [Speaker]); this class only manages voices, through the engine's
 * documented voice-management provider (docs/SPEECH_ENGINE_API.md), which only apps signed with
 * the same key may use.
 */
class EngineClient(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** True if the engine is installed and lets this app manage its voices. */
    fun canManage(): Boolean =
        isInstalled() && context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun voices(): List<EngineVoice> {
        if (!canManage()) return emptyList()
        return try {
            resolver.query(Uri.parse("content://$AUTHORITY/voices"), null, null, null, null)?.use { c ->
                fun str(name: String) = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
                buildList {
                    while (c.moveToNext()) {
                        add(
                            EngineVoice(
                                id = str("id") ?: continue,
                                name = str("name") ?: "Voice",
                                kind = str("kind") ?: "stock",
                                speakerName = str("speaker_name"),
                                licence = str("licence"),
                                locale = str("locale") ?: "en-GB",
                                consentSpeaker = str("consent_speaker"),
                                consentTimeMillis = str("consent_time_millis")?.toLongOrNull(),
                                notice = str("notice") ?: "This is an AI-generated voice.",
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** The voice as a plain voice-package ZIP, streamed from the engine. */
    fun openVoice(id: String): InputStream =
        resolver.openInputStream(Uri.parse("content://$AUTHORITY/voices/$id")) ?: throw IOException("The speech engine did not provide voice $id")

    /** Installs (or replaces) a voice from a plain voice-package ZIP. The engine checks it first. */
    fun installVoice(id: String, zip: InputStream) {
        (resolver.openOutputStream(Uri.parse("content://$AUTHORITY/staging/$id"), "w") ?: throw IOException("The speech engine refused the voice"))
            .use { zip.copyTo(it) }
        call("install", id)
    }

    fun deleteVoice(id: String) = call("delete", id)

    fun deleteAll() = call("deleteAll", null)

    /** Opens the engine's own screen to import a voice file (it asks for the file's passphrase). */
    fun importIntent(file: Uri): Intent =
        Intent(ACTION_IMPORT_VOICE, file).setPackage(PACKAGE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun openEngineIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)

    private fun call(method: String, arg: String?) {
        val result = resolver.call(Uri.parse("content://$AUTHORITY"), method, arg, null)
        if (result?.getBoolean("ok") != true) throw IOException(result?.getString("error") ?: "The speech engine did not respond")
    }

    companion object {
        const val PACKAGE = "uk.elizabeth.speech"
        const val AUTHORITY = "uk.elizabeth.speech.voices"
        const val PERMISSION = "uk.elizabeth.speech.permission.MANAGE_VOICES"
        const val ACTION_IMPORT_VOICE = "uk.elizabeth.speech.action.IMPORT_VOICE"
    }
}
