package uk.elizabeth.aac

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.elizabeth.aac.audio.AudioPlayer
import uk.elizabeth.aac.audio.VoiceRecorder
import uk.elizabeth.aac.core.audio.Chime
import uk.elizabeth.aac.core.audio.PcmAudio
import uk.elizabeth.aac.core.audio.QualityReport
import uk.elizabeth.aac.core.audio.RecordingQuality
import uk.elizabeth.aac.core.audio.Wav
import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.voiceformat.WrongPassphraseException
import uk.elizabeth.aac.core.model.AppSettings
import uk.elizabeth.aac.core.model.SpeechSettings
import uk.elizabeth.aac.core.model.Phrase
import uk.elizabeth.aac.core.model.PhraseBoard
import uk.elizabeth.aac.core.predict.WordPredictor
import uk.elizabeth.aac.core.privacy.PinHasher
import uk.elizabeth.aac.core.privacy.PrivacyEventType
import uk.elizabeth.aac.core.text.MessageEditor
import uk.elizabeth.aac.core.touch.Suggestion
import uk.elizabeth.aac.core.touch.TouchAdvisor
import uk.elizabeth.aac.core.touch.TouchEvent
import uk.elizabeth.aac.core.touch.TouchStats
import uk.elizabeth.aac.core.voicebank.VoiceBank
import uk.elizabeth.aac.core.voicebank.VoiceTake
import uk.elizabeth.aac.data.Repository
import uk.elizabeth.aac.speech.EngineClient
import uk.elizabeth.aac.speech.EngineVoice
import uk.elizabeth.aac.speech.Speaker
import uk.elizabeth.voiceformat.VoiceConsent
import java.text.DateFormat
import java.util.Date
import java.util.UUID

enum class Screen { MAIN, SETTINGS, PHRASES, PRIVACY, VOICE_BANK }

/** Tabs on the main screen: a phrase category, recent messages, or the keyboard. */
object Tabs {
    const val RECENT = "tab-recent"
    const val KEYBOARD = "tab-keyboard"
}

/** What a recording is for: a phrase button (message banking) or a voice banking sentence. */
sealed interface RecordTarget {
    data class ForPhrase(val phraseId: String) : RecordTarget
    data class ForPrompt(val promptIndex: Int) : RecordTarget
}

sealed interface PinResult {
    data object Ok : PinResult
    data object Wrong : PinResult
    data class Locked(val seconds: Long) : PinResult
}

/** A recording waiting to be kept or discarded. */
data class PendingRecording(val target: RecordTarget, val audio: PcmAudio, val quality: QualityReport)

data class UiState(
    val loaded: Boolean = false,
    val data: AppData = AppData(),
    val message: String = "",
    val canUndo: Boolean = false,
    val predictions: List<String> = emptyList(),
    val messageSuggestions: List<String> = emptyList(),
    val tab: String = Tabs.KEYBOARD,
    val page: Int = 0,
    val tabPage: Int = 0,
    val symbols: Boolean = false,
    val screen: Screen = Screen.MAIN,
    val attentionOn: Boolean = false,
    /** Shown full-screen if speech fails, so the message still gets across. */
    val fallbackText: String? = null,
    /** A short message for the carer, e.g. "Export saved". */
    val notice: String? = null,
    val learnedWords: List<Pair<String, Int>> = emptyList(),
    val recordingTarget: RecordTarget? = null,
    val recordingLevel: Float = 0f,
    val pendingRecording: PendingRecording? = null,
    /** The voice banking sentence currently shown. */
    val promptIndex: Int = 0,
    /** Voices installed in the Piper Voice Engine app (her own voice, donor or published voices). */
    val installedVoices: List<EngineVoice> = emptyList(),
    /** Whether the Piper Voice Engine app is installed. */
    val engineInstalled: Boolean = false,
    /** True while a backup is being written, checked or restored. */
    val backupBusy: Boolean = false,
)


class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application)
    val speaker = Speaker(application)
    private val player = AudioPlayer()
    private val recorder = VoiceRecorder()
    private val editor = MessageEditor()
    private val predictor = WordPredictor(WordPredictor.loadSeedWords())
    private val chime = Chime.generate()
    private val engine = EngineClient(application)

    /** The voice banking script: the bundled sentences plus any the family added. */
    private val script = VoiceBank.loadScript()
    fun voicePrompts(): List<String> = _state.value.data.voiceBank.prompts(script)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val settings: AppSettings get() = _state.value.data.settings

    // Saves are queued and coalesced so the newest data is always written last.
    private val saveData = Channel<Unit>(Channel.CONFLATED)
    private val saveWords = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            val loaded = repository.load()
            predictor.import(loaded.wordModel)
            var data = loaded.data
            if (loaded.isFirstRun) data = data.copy(privacyLog = data.privacyLog.append(PrivacyEventType.APP_FIRST_RUN, now()))
            val firstTab = data.board.categories.firstOrNull()?.id ?: Tabs.KEYBOARD
            _state.update { it.copy(loaded = true, data = data, tab = firstTab, notice = loaded.problem) }
            configureSpeech(data.settings.speech)
            refreshVoices()
            refreshText()
            if (loaded.isFirstRun) saveData.trySend(Unit)
        }
        viewModelScope.launch { for (x in saveData) repository.saveAppData(_state.value.data) }
        viewModelScope.launch { for (x in saveWords) repository.saveWordModel(predictor.export()) }
    }

    // ---- Communication ----

    fun onQuickReply(phrase: Phrase) = say(phrase)

    fun onPhrase(phrase: Phrase) {
        if (phrase.recordingId != null || settings.speakPhrasesImmediately) {
            say(phrase)
        } else {
            editor.insertPhrase(phrase.text)
            refreshText()
        }
    }

    fun onRecent(text: String) {
        if (settings.speakPhrasesImmediately) sayText(text) else {
            editor.insertPhrase(text)
            refreshText()
        }
    }

    fun onWord(word: String) = edit { insertWord(word) }
    fun onMessageSuggestion(text: String) = edit { replaceAll("$text ") }
    fun onKey(c: Char) = edit { typeCharacter(c) }
    fun onBackspace() = edit { backspace() }
    fun onDeleteWord() = edit { deleteWord() }
    fun onClear() = edit { clear() }
    fun onUndo() = edit { undo() }

    fun onSpeak() {
        val text = editor.text.trim()
        if (text.isEmpty()) return
        sayText(text)
        if (settings.clearAfterSpeaking) edit { clear() }
    }

    /** Repeats the last thing she said. */
    fun onRepeat() {
        _state.value.data.history.entries.firstOrNull()?.let { speakAloud(it.text) }
    }

    fun onAttention() {
        if (_state.value.attentionOn) {
            player.stop()
            _state.update { it.copy(attentionOn = false) }
            return
        }
        speaker.stop()
        _state.update { it.copy(attentionOn = true) }
        player.play(chime, loop = settings.attentionRepeats) { _state.update { it.copy(attentionOn = false) } }
    }

    fun dismissFallback() = _state.update { it.copy(fallbackText = null) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }
    fun showNotice(text: String) = notify(text)

    fun selectTab(tab: String) = _state.update { it.copy(tab = tab, page = 0, symbols = false) }
    fun changePage(delta: Int) = _state.update { it.copy(page = (it.page + delta).coerceAtLeast(0)) }
    fun changeTabPage(delta: Int) = _state.update { it.copy(tabPage = (it.tabPage + delta).coerceAtLeast(0)) }
    fun toggleSymbols() = _state.update { it.copy(symbols = !it.symbols) }

    private fun say(phrase: Phrase) {
        val recordingId = phrase.recordingId
        if (recordingId == null) return sayText(phrase.text)
        viewModelScope.launch {
            val audio = repository.loadRecording(recordingId)?.let { runCatching { Wav.decode(it) }.getOrNull() }
            if (audio != null) {
                speaker.stop()
                player.play(audio)
            } else {
                speakAloud(phrase.text) // recording missing: fall back to the synthetic voice
            }
        }
        remember(phrase.text)
    }

    private fun sayText(text: String) {
        speakAloud(text)
        remember(text)
    }

    private fun speakAloud(text: String) {
        player.stop()
        _state.update { it.copy(attentionOn = false) }
        // Installed voices (including her own) speak through the separate Piper Voice Engine app,
        // via Android's standard text-to-speech system, like any other speech engine.
        if (!speaker.speak(text)) _state.update { it.copy(fallbackText = text) }
    }

    // ---- Voices (installed in the Piper Voice Engine app) ----

    private fun configureSpeech(speech: SpeechSettings) = speaker.configure(speech)

    /** Re-reads the voices installed in the engine app (e.g. after returning from importing one). */
    fun refreshVoices() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { engine.isInstalled() }
            val voices = withContext(Dispatchers.IO) { engine.voices() }
            _state.update { it.copy(engineInstalled = installed, installedVoices = voices) }
        }
    }

    /** Speak with a voice installed in the engine app, or null for the tablet's own speech voice. */
    fun chooseVoice(voiceId: String?) = updateSettings {
        it.copy(speech = it.speech.copy(enginePackage = if (voiceId == null) null else EngineClient.PACKAGE, voiceName = voiceId))
    }

    fun deleteVoice(id: String) {
        if (settings.speech.voiceName == id) chooseVoice(null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { engine.deleteVoice(id) } }
                .onSuccess { updateData { it.copy(privacyLog = it.privacyLog.append(PrivacyEventType.VOICE_MODEL_REMOVED, now())) } }
                .onFailure { e -> notify("Could not remove the voice: ${e.message}") }
            refreshVoices()
        }
    }

    /** Adds a spoken message to history and to the word model, if she has allowed that. */
    private fun remember(text: String) {
        if (settings.historyEnabled) {
            updateData { it.copy(history = it.history.add(text, now(), it.settings.historySize)) }
        }
        if (settings.learningEnabled) {
            predictor.learn(MessageEditor.tokenize(text))
            saveWords.trySend(Unit)
        }
    }

    private fun edit(change: MessageEditor.() -> Unit) {
        editor.change()
        refreshText()
    }

    private fun refreshText() {
        val s = settings
        val predictions = if (s.predictionCount == 0) emptyList() else
            predictor.predict(editor.precedingWords, editor.currentWordPrefix, s.predictionCount)
        val suggestions = _state.value.data.history.suggest(editor.text, 2)
        _state.update {
            it.copy(message = editor.text, canUndo = editor.canUndo, predictions = predictions, messageSuggestions = suggestions)
        }
    }

    // ---- Navigation and carer PIN ----

    fun openScreen(screen: Screen) {
        if (screen == Screen.PRIVACY) refreshLearnedWords()
        _state.update { it.copy(screen = screen) }
    }

    fun needsPin(): Boolean = settings.carerPinHash != null

    /**
     * Checks the carer PIN. Repeated wrong PINs lock entry for a growing time, and the lock
     * survives restarting the app.
     */
    fun checkPin(pin: String): PinResult {
        val hash = settings.carerPinHash ?: return PinResult.Ok
        val guard = _state.value.data.pinGuard
        val time = now()
        if (guard.isLocked(time)) return PinResult.Locked(guard.secondsLeft(time))
        val ok = PinHasher.verify(pin, hash)
        val next = if (ok) guard.afterSuccess() else guard.afterFailure(time)
        updateData { it.copy(pinGuard = next) }
        return when {
            ok -> PinResult.Ok
            next.isLocked(time) -> PinResult.Locked(next.secondsLeft(time))
            else -> PinResult.Wrong
        }
    }

    fun setPin(pin: String?) {
        val hash = pin?.takeIf { it.length >= 4 }?.let { PinHasher.hash(it) }
        updateData {
            it.copy(
                settings = it.settings.copy(carerPinHash = hash),
                privacyLog = it.privacyLog.append(if (hash != null) PrivacyEventType.CARER_PIN_SET else PrivacyEventType.CARER_PIN_REMOVED, now()),
            )
        }
    }

    // ---- Settings ----

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val old = settings
        val new = change(old).sanitised()
        var log = _state.value.data.privacyLog
        if (old.learningEnabled != new.learningEnabled) {
            log = log.append(if (new.learningEnabled) PrivacyEventType.LEARNING_ENABLED else PrivacyEventType.LEARNING_DISABLED, now())
        }
        if (old.historyEnabled != new.historyEnabled) {
            log = log.append(if (new.historyEnabled) PrivacyEventType.HISTORY_ENABLED else PrivacyEventType.HISTORY_DISABLED, now())
        }
        updateData { it.copy(settings = new, privacyLog = log, history = it.history.trimmedTo(new.historySize)) }
        if (old.speech != new.speech) configureSpeech(new.speech)
        refreshText()
    }

    // ---- Touch statistics and suggestions ----

    fun recordTouch(event: TouchEvent) {
        if (!settings.touchStatsEnabled) return
        updateData { d ->
            val stats = if (d.touchStats.sinceMillis == 0L) TouchStats(sinceMillis = now()) else d.touchStats
            d.copy(touchStats = stats.add(event))
        }
    }

    fun touchSuggestions(): List<Suggestion> = TouchAdvisor.suggestions(_state.value.data.touchStats, settings)

    fun applySuggestion(suggestion: Suggestion) {
        updateSettings(suggestion.apply)
        resetTouchStats()
        notify("Setting changed. Counting starts again, to see how the change works for her.")
    }

    fun resetTouchStats() = updateData { it.copy(touchStats = TouchStats()) }

    fun testVoice() = speakAloud("Hello. This is how my voice sounds.")

    // ---- Phrase editing ----

    fun editBoard(change: (PhraseBoard) -> PhraseBoard) {
        updateData { it.copy(board = change(it.board)) }
        viewModelScope.launch { deleteUnusedRecordings() }
    }

    fun startRecording(target: RecordTarget) {
        if (_state.value.recordingTarget != null) return
        player.stop()
        _state.update { it.copy(recordingTarget = target, recordingLevel = 0f, pendingRecording = null) }
        viewModelScope.launch {
            val result = runCatching { recorder.record { level -> _state.update { it.copy(recordingLevel = level) } } }
            _state.update { it.copy(recordingTarget = null, recordingLevel = 0f) }
            result.onSuccess { raw ->
                val audio = RecordingQuality.trimSilence(raw)
                _state.update { it.copy(pendingRecording = PendingRecording(target, audio, RecordingQuality.check(raw))) }
            }.onFailure { e -> _state.update { it.copy(notice = "Recording failed: ${e.message}") } }
        }
    }

    fun stopRecording() = recorder.requestStop()

    fun playPending() {
        _state.value.pendingRecording?.let { player.play(it.audio) }
    }

    fun discardPending() = _state.update { it.copy(pendingRecording = null) }

    fun keepPending() {
        val pending = _state.value.pendingRecording ?: return
        _state.update { it.copy(pendingRecording = null) }
        val id = UUID.randomUUID().toString()
        val wav = Wav.encode(pending.audio)
        viewModelScope.launch {
            when (val target = pending.target) {
                is RecordTarget.ForPhrase -> {
                    repository.saveRecording(id, wav)
                    updateData {
                        it.copy(
                            board = it.board.updatePhrase(target.phraseId) { p -> p.copy(recordingId = id) },
                            privacyLog = it.privacyLog.append(PrivacyEventType.RECORDING_ADDED, now()),
                        )
                    }
                    deleteUnusedRecordings()
                }
                is RecordTarget.ForPrompt -> {
                    repository.saveVoiceTake(id, wav)
                    val replaced = _state.value.data.voiceBank.takeFor(target.promptIndex)
                    val take = VoiceTake(target.promptIndex, id, pending.audio.durationMs, pending.quality.isGood, now())
                    updateData { it.copy(voiceBank = it.voiceBank.withTake(take)) }
                    replaced?.let { repository.deleteVoiceTake(it.recordingId) }
                    // Move straight on to the next sentence still to record.
                    val next = _state.value.data.voiceBank.nextUnrecorded(target.promptIndex + 1, voicePrompts().size)
                    if (next != null) _state.update { it.copy(promptIndex = next) }
                }
            }
        }
    }

    /** Plays a phrase's recording for checking, without adding it to history or learning. */
    fun playRecording(phrase: Phrase) {
        val id = phrase.recordingId ?: return
        viewModelScope.launch {
            val audio = repository.loadRecording(id)?.let { runCatching { Wav.decode(it) }.getOrNull() }
            if (audio != null) player.play(audio) else notify("The recording could not be read.")
        }
    }

    fun removeRecording(phraseId: String) {
        updateData {
            it.copy(
                board = it.board.updatePhrase(phraseId) { p -> p.copy(recordingId = null) },
                privacyLog = it.privacyLog.append(PrivacyEventType.RECORDING_DELETED, now()),
            )
        }
        viewModelScope.launch { deleteUnusedRecordings() }
    }

    private suspend fun deleteUnusedRecordings() {
        val unused = _state.value.data.board.unusedRecordings(repository.recordingIds())
        unused.forEach { repository.deleteRecording(it) }
    }

    // ---- Voice banking ----

    fun recordVoiceConsent(speakerName: String, isSelf: Boolean) {
        val name = speakerName.trim()
        if (name.isEmpty()) return
        val consent = VoiceConsent(name, isSelf, VoiceConsent.statementFor(name, isSelf), now())
        updateData {
            it.copy(
                voiceBank = it.voiceBank.copy(consent = consent),
                privacyLog = it.privacyLog.append(PrivacyEventType.VOICE_CONSENT_RECORDED, now()),
            )
        }
        val first = _state.value.data.voiceBank.nextUnrecorded(0, voicePrompts().size) ?: 0
        _state.update { it.copy(promptIndex = first) }
    }

    /** Withdraws consent: deletes every voice banking recording and the consent itself. */
    /** Withdraws consent: deletes the recordings, the consent, and any voice on this tablet made from them. */
    fun withdrawVoiceConsent() {
        val bank = _state.value.data.voiceBank
        viewModelScope.launch {
            repository.deleteAllVoiceTakes()
            val madeFromThem = withContext(Dispatchers.IO) {
                engine.voices().filter { v ->
                    val speaker = v.consentSpeaker
                    val time = v.consentTimeMillis
                    speaker != null && time != null && bank.isSourceOf(VoiceConsent(speaker, true, "", time))
                }
            }
            if (madeFromThem.any { it.id == settings.speech.voiceName }) chooseVoice(null)
            val notRemoved = withContext(Dispatchers.IO) {
                madeFromThem.filter { v -> runCatching { engine.deleteVoice(v.id) }.isFailure }
            }
            if (notRemoved.isNotEmpty()) notify("Please remove these voices in the Piper Voice Engine app: ${notRemoved.joinToString { it.name }}")
            updateData { d ->
                var log = d.privacyLog.append(PrivacyEventType.VOICE_CONSENT_WITHDRAWN, now())
                madeFromThem.forEach { log = log.append(PrivacyEventType.VOICE_MODEL_REMOVED, now()) }
                d.copy(voiceBank = VoiceBank(), privacyLog = log)
            }
            refreshVoices()
            _state.update { it.copy(promptIndex = 0) }
            notify(
                "Voice banking recordings deleted" +
                    if (madeFromThem.isEmpty()) "." else ", and ${madeFromThem.size} voice(s) made from them removed from this tablet.",
            )
        }
    }

    fun goToPrompt(index: Int) {
        val count = voicePrompts().size
        if (count > 0) _state.update { it.copy(promptIndex = index.mod(count), pendingRecording = null) }
    }

    fun goToNextUnrecorded() {
        val next = _state.value.data.voiceBank.nextUnrecorded(_state.value.promptIndex + 1, voicePrompts().size)
        if (next != null) goToPrompt(next) else notify("Every sentence has been recorded")
    }

    fun addVoicePrompt(text: String) = updateData { it.copy(voiceBank = it.voiceBank.addPrompt(text)) }

    fun playTake(promptIndex: Int) {
        val take = _state.value.data.voiceBank.takeFor(promptIndex) ?: return
        viewModelScope.launch {
            val audio = repository.loadVoiceTake(take.recordingId)?.let { runCatching { Wav.decode(it) }.getOrNull() }
            if (audio != null) player.play(audio) else notify("The recording could not be read.")
        }
    }

    fun deleteTake(promptIndex: Int) {
        val take = _state.value.data.voiceBank.takeFor(promptIndex) ?: return
        updateData { it.copy(voiceBank = it.voiceBank.withoutTake(promptIndex)) }
        viewModelScope.launch { repository.deleteVoiceTake(take.recordingId) }
    }

    fun exportTrainingData(uri: Uri, passphrase: CharArray) {
        val bank = _state.value.data.voiceBank
        viewModelScope.launch {
            runCatching {
                val out = openOutput(uri)
                repository.exportTrainingData(out, bank, voicePrompts(), passphrase, now())
            }.onSuccess { count ->
                updateData { it.copy(privacyLog = it.privacyLog.append(PrivacyEventType.VOICE_TRAINING_DATA_EXPORTED, now(), "$count recordings")) }
                notify("Exported $count recordings for voice training. Keep the file and passphrase safe.")
            }.onFailure { e -> notify("Export failed: ${e.message}") }
            passphrase.fill(' ')
        }
    }

    // ---- Privacy: see, export, erase ----

    private fun refreshLearnedWords() = _state.update { it.copy(learnedWords = predictor.learnedWords()) }

    fun forgetWord(word: String) {
        predictor.forget(word)
        saveWords.trySend(Unit)
        updateData { it.copy(privacyLog = it.privacyLog.append(PrivacyEventType.WORD_FORGOTTEN, now())) }
        refreshLearnedWords()
        refreshText()
    }

    fun eraseLearnedWords() {
        predictor.forgetAll()
        saveWords.trySend(Unit)
        updateData { it.copy(privacyLog = it.privacyLog.append(PrivacyEventType.LEARNED_WORDS_ERASED, now())) }
        refreshLearnedWords()
        refreshText()
        notify("Learned words erased")
    }

    fun eraseHistory() {
        updateData { it.copy(history = it.history.trimmedTo(0), privacyLog = it.privacyLog.append(PrivacyEventType.HISTORY_ERASED, now())) }
        refreshText()
        notify("Message history erased")
    }

    /** Deletes all her data and the encryption key, then starts again with defaults. */
    fun eraseEverything() {
        viewModelScope.launch {
            repository.eraseAll()
            withContext(Dispatchers.IO) { if (engine.canManage()) runCatching { engine.deleteAll() } }
            predictor.forgetAll()
            editor.clear()
            val fresh = AppData().let { it.copy(privacyLog = it.privacyLog.append(PrivacyEventType.ALL_DATA_ERASED, now())) }
            _state.update { UiState(loaded = true, data = fresh, tab = fresh.board.categories.first().id, screen = Screen.MAIN) }
            configureSpeech(fresh.settings.speech)
            refreshText()
            saveData.trySend(Unit)
            saveWords.trySend(Unit)
        }
    }

    /**
     * Writes an encrypted backup to [uri] (a USB stick, the Google Drive app, or any storage the
     * carer picks: the app itself never uses the network). Afterwards every recording now in the
     * backup counts as saved.
     */
    fun exportTo(uri: Uri, passphrase: CharArray, includeVoices: Boolean) {
        val data = _state.value.data
        val recordingIds = data.recordingIds()
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            runCatching {
                val voices = if (includeVoices) withContext(Dispatchers.IO) { engine.voices().map { it.id } } else emptyList()
                repository.exportBackup(openOutput(uri), data, predictor.export(), passphrase, voices, engine::openVoice, now())
            }.onSuccess { info ->
                updateData {
                    it.copy(
                        backupStatus = it.backupStatus.afterBackup(now(), recordingIds),
                        privacyLog = it.privacyLog.append(PrivacyEventType.DATA_EXPORTED, now(), "${info.recordings + info.voiceTakes} recordings, ${info.voices} voices"),
                    )
                }
                notify(
                    "Backup saved: ${info.recordings + info.voiceTakes} recordings" +
                        (if (info.voices > 0) " and ${info.voices} voice(s)" else "") +
                        ". Keep the passphrase safe and separate: without it the backup cannot be opened.",
                )
            }.onFailure { e -> notify("Backup failed: ${e.message}. Nothing was marked as backed up.") }
            _state.update { it.copy(backupBusy = false) }
            passphrase.fill(' ')
        }
    }

    /** Reads a whole backup and checks it, without changing anything. */
    fun checkBackup(uri: Uri, passphrase: CharArray) {
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                (resolver.openInputStream(uri) ?: error("Could not open the file")).use { repository.checkBackup(it, passphrase) }
            }.onSuccess { info ->
                val date = if (info.createdAtMillis > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(info.createdAtMillis)) else "an unknown date"
                notify("✓ This backup is complete and readable. Made $date, with ${info.recordings} recorded phrases, ${info.voiceTakes} voice banking recordings and ${info.voices} voice(s).")
            }.onFailure { e ->
                notify(if (e is WrongPassphraseException) "✗ Wrong passphrase, or the backup is damaged." else "✗ This backup cannot be used: ${e.message}")
            }
            _state.update { it.copy(backupBusy = false) }
            passphrase.fill(' ')
        }
    }

    fun importFrom(uri: Uri, passphrase: CharArray) {
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            val log = _state.value.data.privacyLog
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                repository.restoreBackup(
                    { resolver.openInputStream(uri) ?: error("Could not open the file") }, passphrase,
                    installVoice = if (engine.canManage()) engine::installVoice else null,
                )
            }.onSuccess { result ->
                // Keep this tablet's privacy log and record the restore in it. Everything restored
                // is, by definition, in that backup.
                val data = result.data.copy(
                    privacyLog = log.append(PrivacyEventType.DATA_RESTORED, now()),
                    backupStatus = result.data.backupStatus.afterBackup(result.info.createdAtMillis, result.data.recordingIds()),
                )
                predictor.import(result.wordModel)
                _state.update { it.copy(data = data, tab = data.board.categories.firstOrNull()?.id ?: Tabs.KEYBOARD, page = 0, promptIndex = 0) }
                saveData.trySend(Unit)
                configureSpeech(data.settings.speech)
                refreshVoices()
                refreshText()
                notify(
                    if (result.failedVoices.isEmpty()) "Data restored"
                    else "Data restored, but ${result.failedVoices.size} voice(s) could not be installed. " +
                        if (engine.canManage()) "They failed the speech engine's checks." else "Install the Piper Voice Engine app, then restore again.",
                )
            }.onFailure { e ->
                notify(if (e is WrongPassphraseException) "Wrong passphrase, or the file is damaged. Nothing was changed." else "Could not restore: ${e.message}")
            }
            _state.update { it.copy(backupBusy = false) }
            passphrase.fill(' ')
        }
    }

    fun saveKeyguard(uri: Uri, svg: String) {
        viewModelScope.launch {
            runCatching { openOutput(uri).use { it.write(svg.toByteArray()) } }
                .onSuccess { notify("Keyguard template saved. Cut it at 100% scale, and check it against the screen before cutting acrylic.") }
                .onFailure { e -> notify("Could not save: ${e.message}") }
        }
    }

    private suspend fun openOutput(uri: Uri) = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file")
    }

    // ---- Helpers ----

    private fun updateData(change: (AppData) -> AppData) {
        _state.update { it.copy(data = change(it.data)) }
        saveData.trySend(Unit)
    }

    private fun notify(text: String) = _state.update { it.copy(notice = text) }

    private fun now() = System.currentTimeMillis()

    override fun onCleared() {
        player.stop()
        speaker.shutdown()
    }
}
