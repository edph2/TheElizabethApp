package uk.elizabeth.aac.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.elizabeth.aac.core.model.AppSettings
import uk.elizabeth.aac.core.model.History
import uk.elizabeth.aac.core.model.PhraseBoard
import uk.elizabeth.aac.core.privacy.PrivacyLog

/** Everything the app stores except recordings and the word model, which are kept in their own files. */
@Serializable
data class AppData(
    val version: Int = CURRENT_VERSION,
    val settings: AppSettings = AppSettings(),
    val board: PhraseBoard = PhraseBoard.default(),
    val history: History = History(),
    val privacyLog: PrivacyLog = PrivacyLog(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun fromJson(text: String): AppData = json.decodeFromString(serializer(), text).let {
            it.copy(settings = it.settings.sanitised())
        }
    }
}
