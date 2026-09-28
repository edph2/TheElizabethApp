// SPDX-License-Identifier: GPL-3.0-or-later
// Piper Voice Engine. Free software under the GNU GPL v3 or later: see engine/LICENSE.
package uk.elizabeth.speech

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.MissingResourceException

/**
 * A standard Android text-to-speech engine for installed Piper voices. Any app can use it
 * through Android's TextToSpeech API; it runs entirely on the device and has no network access.
 */
class PiperTtsService : TextToSpeechService() {
    private lateinit var models: VoiceModels
    private val lock = Any()
    private var loaded: PiperVoice? = null
    private var loadedGeneration = -1
    @Volatile private var selectedId: String? = null
    @Volatile private var stopped = false

    override fun onCreate() {
        // Must exist before super.onCreate(), which asks for the default language.
        models = VoiceModels(this)
        super.onCreate()
    }

    override fun onDestroy() {
        // Stop any synthesis first, then wait for it to finish before freeing the native voice.
        stopped = true
        synchronized(lock) {
            loaded?.release()
            loaded = null
        }
        super.onDestroy()
    }

    // ---- Languages ----

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        availability(models.list(), lang, country)

    override fun onGetLanguage(): Array<String> {
        val voice = selectedId?.let { models.find(it) } ?: models.list().firstOrNull()
        val locale = voice?.let { localeOf(it) } ?: Locale.UK
        return arrayOf(iso3Language(locale), iso3Country(locale), "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val voices = models.list()
        val result = availability(voices, lang, country)
        if (result >= TextToSpeech.LANG_AVAILABLE) selectedId = bestFor(voices, lang, country)?.id
        return result
    }

    // ---- Voices ----

    override fun onGetVoices(): List<Voice> = models.list().map { v ->
        Voice(v.id, localeOf(v), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
    }

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (voiceName != null && models.find(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int {
        val voice = voiceName?.let { models.find(it) } ?: return TextToSpeech.ERROR
        selectedId = voice.id
        return TextToSpeech.SUCCESS
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? =
        bestFor(models.list(), lang, country)?.id

    // ---- Speaking ----

    override fun onStop() {
        stopped = true
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val voices = models.list()
        val voice = request.voiceName?.let { name -> voices.firstOrNull { it.id == name } }
            ?: selectedId?.let { id -> voices.firstOrNull { it.id == id } }
            ?: bestFor(voices, request.language, request.country)
            ?: voices.firstOrNull()
        if (voice == null) {
            callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
            return
        }
        // The lock is held for the whole utterance, so the voice is never released while in use.
        synchronized(lock) { speak(voice, request, callback) }
    }

    private fun speak(voice: InstalledVoice, request: SynthesisRequest, callback: SynthesisCallback) {
        try {
            val piper = piperFor(voice)
            if (callback.start(piper.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
            val maxBytes = callback.maxBufferSize
            piper.stream(request.charSequenceText?.toString() ?: "", request.speechRate / 100f) { pcm ->
                if (stopped) return@stream false
                val bytes = ByteArray(pcm.size * 2)
                for (i in pcm.indices) {
                    bytes[2 * i] = (pcm[i].toInt() and 0xff).toByte()
                    bytes[2 * i + 1] = (pcm[i].toInt() shr 8 and 0xff).toByte()
                }
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(maxBytes, bytes.size - offset)
                    if (callback.audioAvailable(bytes, offset, n) != TextToSpeech.SUCCESS) {
                        stopped = true
                        return@stream false
                    }
                    offset += n
                }
                !stopped
            }
            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        }
    }

    /** Reuses the loaded voice unless another voice is wanted or the installed voices changed. Call holding [lock]. */
    private fun piperFor(voice: InstalledVoice): PiperVoice {
        val current = loaded
        if (current != null && current.voice.id == voice.id && loadedGeneration == VoiceChanges.generation) return current
        current?.release()
        loaded = null
        return PiperVoice(voice).also {
            loaded = it
            loadedGeneration = VoiceChanges.generation
        }
    }

    private fun availability(voices: List<InstalledVoice>, lang: String?, country: String?): Int {
        val locales = voices.map { localeOf(it) }
        return when {
            locales.any { matchesLanguage(it, lang) && matchesCountry(it, country) && !country.isNullOrEmpty() } ->
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            locales.any { matchesLanguage(it, lang) } -> TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun bestFor(voices: List<InstalledVoice>, lang: String?, country: String?): InstalledVoice? =
        voices.firstOrNull { matchesLanguage(localeOf(it), lang) && matchesCountry(localeOf(it), country) }
            ?: voices.firstOrNull { matchesLanguage(localeOf(it), lang) }

    private companion object {
        const val TAG = "PiperTtsService"

        fun localeOf(v: InstalledVoice): Locale = Locale.forLanguageTag(v.manifest.locale)

        fun iso3Language(l: Locale) = try { l.isO3Language } catch (e: MissingResourceException) { l.language }
        fun iso3Country(l: Locale) = try { l.isO3Country } catch (e: MissingResourceException) { l.country }

        fun matchesLanguage(l: Locale, lang: String?) =
            !lang.isNullOrEmpty() && (lang.equals(iso3Language(l), true) || lang.equals(l.language, true))

        fun matchesCountry(l: Locale, country: String?) =
            !country.isNullOrEmpty() && (country.equals(iso3Country(l), true) || country.equals(l.country, true))
    }
}

/** Bumped whenever voices are installed or removed, so a loaded voice is reloaded when needed. */
object VoiceChanges {
    @Volatile var generation = 0
        private set

    fun changed() {
        generation++
    }
}
