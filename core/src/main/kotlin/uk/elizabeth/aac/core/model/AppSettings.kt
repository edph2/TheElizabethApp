package uk.elizabeth.aac.core.model

import kotlinx.serialization.Serializable
import uk.elizabeth.aac.core.touch.TouchSettings

@Serializable
enum class KeyboardLayout(val rows: List<String>) {
    ABC(listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz'?")),
    QWERTY(listOf("qwertyuiop", "asdfghjkl'", "zxcvbnm?")),
}

@Serializable
data class SpeechSettings(
    /** Android TTS engine package, or null for the system default. */
    val enginePackage: String? = null,
    /** Voice name within the engine, or null for the engine's default. */
    val voiceName: String? = null,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
)

@Serializable
data class AppSettings(
    val touch: TouchSettings = TouchSettings(),
    val speech: SpeechSettings = SpeechSettings(),
    val gridColumns: Int = 4,
    val gridRows: Int = 3,
    val keyboardLayout: KeyboardLayout = KeyboardLayout.ABC,
    /** Speak a phrase as soon as it is pressed, instead of adding it to the message. */
    val speakPhrasesImmediately: Boolean = false,
    /** Clear the message after it has been spoken. */
    val clearAfterSpeaking: Boolean = false,
    val predictionCount: Int = 5,
    val learningEnabled: Boolean = true,
    val historyEnabled: Boolean = true,
    val historySize: Int = 200,
    val highContrast: Boolean = true,
    /** Multiplier for text size on buttons and the message bar. */
    val textScale: Float = 1.0f,
    /** Show the message upside down at the top for someone sitting opposite. */
    val listenerView: Boolean = false,
    /** Repeat the attention chime until someone dismisses it. */
    val attentionRepeats: Boolean = false,
    /** PBKDF2 hash of the carer PIN protecting Settings, or null for no PIN. */
    val carerPinHash: String? = null,
) {
    fun sanitised(): AppSettings = copy(
        touch = touch.sanitised(),
        speech = speech.copy(rate = speech.rate.coerceIn(0.3f, 2.5f), pitch = speech.pitch.coerceIn(0.5f, 2.0f)),
        gridColumns = gridColumns.coerceIn(2, 6),
        gridRows = gridRows.coerceIn(2, 5),
        predictionCount = predictionCount.coerceIn(0, 7),
        historySize = historySize.coerceIn(10, 2_000),
        textScale = textScale.coerceIn(0.7f, 2.0f),
    )

    val phrasesPerPage: Int get() = gridColumns * gridRows
}
