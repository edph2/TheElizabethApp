package uk.elizabeth.aac.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import uk.elizabeth.aac.core.model.SpeechSettings
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class EngineOption(val packageName: String, val label: String)

data class VoiceOption(val name: String, val label: String)

data class SpeakerState(
    val ready: Boolean = false,
    val speaking: Boolean = false,
    val engines: List<EngineOption> = emptyList(),
    val voices: List<VoiceOption> = emptyList(),
    val currentVoice: String? = null,
    val problem: String? = null,
)

/**
 * Speaks text with an Android text-to-speech engine installed on the tablet.
 *
 * Privacy: only voices that work without a network connection are ever used. If the chosen
 * voice needs the internet, it is refused and an offline voice is used instead. (The app has
 * no internet permission, but a speech engine is a separate app, so this is checked too.)
 */
class Speaker(private val context: Context) {
    private val _state = MutableStateFlow(SpeakerState())
    val state: StateFlow<SpeakerState> = _state

    private var tts: TextToSpeech? = null
    private var enginePackage: String? = null
    private var settings = SpeechSettings()
    private val utteranceCounter = AtomicInteger()

    fun configure(newSettings: SpeechSettings) {
        val engineChanged = tts == null || newSettings.enginePackage != enginePackage
        settings = newSettings
        if (engineChanged) start(newSettings.enginePackage) else applySettings()
    }

    /** Speaks [text], interrupting anything already being said. Returns false if speech is unavailable. */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!_state.value.ready) return false
        if (engine.voice?.isNetworkConnectionRequired == true) return false
        val id = "u" + utteranceCounter.incrementAndGet()
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts?.stop()
        _state.update { it.copy(speaking = false) }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    private fun start(engine: String?) {
        tts?.shutdown()
        enginePackage = engine
        _state.update { it.copy(ready = false, voices = emptyList(), problem = null) }
        lateinit var created: TextToSpeech
        created = TextToSpeech(context, { status ->
            if (tts !== created) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                _state.update { it.copy(engines = created.engines.map { e -> EngineOption(e.name, e.label) }) }
                applySettings()
            } else {
                _state.update { it.copy(ready = false, problem = "The speech engine could not start.") }
            }
        }, engine)
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = _state.update { it.copy(speaking = true) }
            override fun onDone(utteranceId: String?) = _state.update { it.copy(speaking = false) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) = _state.update { it.copy(speaking = false) }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = _state.update { it.copy(speaking = false) }
            override fun onError(utteranceId: String?, errorCode: Int) = _state.update { it.copy(speaking = false) }
        })
        tts = created
    }

    private fun applySettings() {
        val engine = tts ?: return
        val offline = offlineVoices(engine)
        val chosen = offline.firstOrNull { it.name == settings.voiceName }
            ?: engine.defaultVoice?.takeIf { v -> offline.any { it.name == v.name } }
            ?: offline.firstOrNull()
        if (chosen == null) {
            _state.update {
                it.copy(ready = false, voices = emptyList(), currentVoice = null,
                    problem = "No offline voice is installed. Install a voice in Android settings, or choose another speech engine.")
            }
            return
        }
        engine.setVoice(chosen)
        engine.setSpeechRate(settings.rate)
        engine.setPitch(settings.pitch)
        _state.update {
            it.copy(ready = true, currentVoice = chosen.name, problem = null,
                voices = offline.map { v -> VoiceOption(v.name, describe(v)) })
        }
    }

    private fun offlineVoices(engine: TextToSpeech): List<Voice> =
        (engine.voices ?: emptySet<Voice>())
            .filter { !it.isNetworkConnectionRequired }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .sortedWith(compareBy<Voice>({ localeRank(it.locale) }, { it.locale.displayName }, { it.name }))

    private fun localeRank(locale: Locale) = when {
        locale.language == "en" && locale.country == "GB" -> 0
        locale.language == "en" -> 1
        else -> 2
    }

    private fun describe(v: Voice) = "${v.locale.getDisplayName(Locale.UK)} · ${v.name}"
}
