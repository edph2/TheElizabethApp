// SPDX-License-Identifier: GPL-3.0-or-later
// Piper Voice Engine. Free software under the GNU GPL v3 or later: see engine/LICENSE.
package uk.elizabeth.speech

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * A small, documented interface for a companion app to manage voices: list them, back them up,
 * restore them and remove them. Protected by a signature permission, so only apps signed with
 * the same key can use it. Speaking itself goes through Android's standard TextToSpeech API.
 *
 *   content://uk.elizabeth.speech.voices/voices          list installed voices (query)
 *   content://uk.elizabeth.speech.voices/voices/<id>     read: the voice as a plain ZIP
 *   content://uk.elizabeth.speech.voices/staging/<id>    write: a plain ZIP to install
 *   call("install", id)  installs the staged ZIP; call("delete", id); call("deleteAll")
 *
 * See docs/SPEECH_ENGINE_API.md.
 */
class VoiceProvider : ContentProvider() {
    private lateinit var models: VoiceModels
    private val staging get() = File(context!!.cacheDir, "staging").apply { mkdirs() }

    override fun onCreate(): Boolean {
        models = VoiceModels(context!!)
        models.discardPartial()
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        if (uri.pathSegments != listOf("voices")) throw IllegalArgumentException("Unknown URI $uri")
        val cursor = MatrixCursor(COLUMNS)
        for (v in models.list()) {
            val m = v.manifest
            cursor.addRow(arrayOf<Any?>(v.id, m.name, m.kind, m.speakerName, m.licence, m.locale, m.consent?.speakerName, m.consent?.timeMillis, m.syntheticVoiceNotice))
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val segments = uri.pathSegments
        if (segments.size != 2 || !segments[1].matches(ID)) throw FileNotFoundException("Unknown URI $uri")
        val id = segments[1]
        return when {
            segments[0] == "voices" && mode == "r" -> {
                val voice = models.find(id) ?: throw FileNotFoundException("No voice $id")
                val (read, write) = ParcelFileDescriptor.createReliablePipe()
                thread(name = "voice-export") {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                        try {
                            models.exportPlain(voice, out)
                        } catch (e: Exception) {
                            write.closeWithError(e.message ?: "Export failed")
                        }
                    }
                }
                read
            }
            segments[0] == "staging" && mode.startsWith("w") -> ParcelFileDescriptor.open(
                File(staging, "$id.zip"),
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            )
            else -> throw FileNotFoundException("Unsupported: $uri ($mode)")
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val result = Bundle()
        try {
            when (method) {
                "install" -> {
                    val id = arg?.takeIf { it.matches(ID) } ?: throw IllegalArgumentException("Bad id")
                    val file = File(staging, "$id.zip")
                    try {
                        file.inputStream().use { models.installPlain(id, it) }
                    } finally {
                        file.delete()
                    }
                }
                "delete" -> models.delete(arg?.takeIf { it.matches(ID) } ?: throw IllegalArgumentException("Bad id"))
                "deleteAll" -> models.deleteAll()
                else -> throw IllegalArgumentException("Unknown method $method")
            }
            VoiceChanges.changed()
            result.putBoolean("ok", true)
        } catch (e: Exception) {
            result.putBoolean("ok", false)
            result.putString("error", e.message ?: e.javaClass.simpleName)
        }
        return result
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    private companion object {
        val ID = Regex("[A-Za-z0-9-]{1,64}")
        val COLUMNS = arrayOf("id", "name", "kind", "speaker_name", "licence", "locale", "consent_speaker", "consent_time_millis", "notice")
    }
}
