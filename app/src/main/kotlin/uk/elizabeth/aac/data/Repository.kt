package uk.elizabeth.aac.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.aac.core.data.Backup
import uk.elizabeth.aac.core.data.BackupInfo
import uk.elizabeth.aac.core.voicebank.TrainingExport
import uk.elizabeth.aac.core.voicebank.VoiceBank
import uk.elizabeth.aac.speech.InstalledVoice
import uk.elizabeth.aac.speech.VoiceModels
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Result of loading stored data. [problem] is set if saved data could not be read. */
class LoadResult(val data: AppData, val wordModel: String, val isFirstRun: Boolean, val problem: String?)

/** Result of restoring an export. [failedVoices] names voices in the backup that did not pass their checks. */
class RestoreResult(val data: AppData, val wordModel: String, val info: BackupInfo, val failedVoices: List<String> = emptyList())

/**
 * All reading and writing of her data. Everything goes through [SecureStore], so it is all
 * encrypted. There are two kinds of recording: phrases recorded for message banking
 * ("rec-") and voice banking takes ("vb-").
 */
class Repository(context: Context) {
    private val store = SecureStore(context)
    private val mutex = Mutex()

    suspend fun load(): LoadResult = io {
        var problem: String? = null
        val appDataBytes = readOrQuarantine(APP_DATA) { problem = it }
        val data = appDataBytes?.let {
            runCatching { AppData.fromJson(it.decodeToString()) }
                .onFailure { e ->
                    Log.w(TAG, "Stored settings unreadable", e)
                    store.quarantine(APP_DATA)
                    problem = "Saved phrases and settings could not be read, so the defaults are being used."
                }.getOrNull()
        }
        val words = readOrQuarantine(WORDS) { problem = it }?.decodeToString() ?: ""
        LoadResult(data ?: AppData(), words, isFirstRun = appDataBytes == null && problem == null, problem = problem)
    }

    suspend fun saveAppData(data: AppData) = io { store.write(APP_DATA, data.toJson().toByteArray()) }

    suspend fun saveWordModel(text: String) = io { store.write(WORDS, text.toByteArray()) }

    // ---- Message banking: recorded phrases ----

    suspend fun saveRecording(id: String, wav: ByteArray) = io { store.write(file(RECORDING, id), wav) }

    suspend fun loadRecording(id: String): ByteArray? = io { runCatching { store.read(file(RECORDING, id)) }.getOrNull() }

    suspend fun deleteRecording(id: String) = io { store.delete(file(RECORDING, id)) }

    suspend fun recordingIds(): List<String> = io { ids(RECORDING) }

    // ---- Voice banking takes ----

    suspend fun saveVoiceTake(id: String, wav: ByteArray) = io { store.write(file(VOICE, id), wav) }

    suspend fun loadVoiceTake(id: String): ByteArray? = io { runCatching { store.read(file(VOICE, id)) }.getOrNull() }

    suspend fun deleteVoiceTake(id: String) = io { store.delete(file(VOICE, id)) }

    suspend fun voiceTakeIds(): List<String> = io { ids(VOICE) }

    suspend fun deleteAllVoiceTakes() = io { store.list(VOICE).forEach { store.delete(it) } }

    // ---- Export, restore, erase ----

    /**
     * Writes the encrypted export of everything to [out], and closes it. Installed voice
     * models are included if [voices] is not empty (they are large, but make one file enough
     * to restore everything). Returns what the backup contains.
     */
    suspend fun exportBackup(
        out: OutputStream,
        data: AppData,
        wordModel: String,
        passphrase: CharArray,
        voiceModels: VoiceModels,
        voices: List<InstalledVoice>,
        now: Long,
    ): BackupInfo = io {
        val recordings = ids(RECORDING)
        val takes = ids(VOICE)
        val info = BackupInfo(now, recordings.size, takes.size, voices.size)
        Backup.write(out, passphrase) { w ->
            w.put(Backup.README_NAME, Backup.README)
            w.put(Backup.INFO, info.toJson())
            w.put(Backup.APP_DATA, data.toJson())
            w.put(Backup.WORDS, wordModel)
            for (id in recordings) store.read(file(RECORDING, id))?.let { w.put(Backup.recordingEntry(id), it) }
            for (id in takes) store.read(file(VOICE, id))?.let { w.put(Backup.voiceBankEntry(id), it) }
            for (voice in voices) {
                for ((path, f) in voiceModels.files(voice)) f.inputStream().use { w.put(Backup.voiceModelEntry(voice.id, path), it) }
            }
        }
        info
    }

    /**
     * Reads a whole backup and checks every part of it (passphrase, every encrypted chunk, the
     * end of the file, the settings) without changing anything. Returns what it contains.
     */
    suspend fun checkBackup(input: InputStream, passphrase: CharArray): BackupInfo = io { scan(input, passphrase) }

    /**
     * Replaces all stored data with a backup. [open] must open the backup afresh each time it
     * is called: the file is read once to check it completely (wrong passphrase, damage,
     * truncation) before anything is changed, then again to restore it. Voices in the backup
     * replace installed voices with the same id; other installed voices are kept.
     * Returns the restored data and the names of any voices that could not be restored.
     */
    suspend fun restoreBackup(open: () -> InputStream, passphrase: CharArray, voiceModels: VoiceModels): RestoreResult = io {
        val info = open().use { scan(it, passphrase) }

        ids(RECORDING).forEach { store.delete(file(RECORDING, it)) }
        ids(VOICE).forEach { store.delete(file(VOICE, it)) }
        voiceModels.discardPartial()
        var data: AppData? = null
        var words = ""
        open().use { input ->
            Backup.readStreaming(input, passphrase) { name, stream ->
                if (!Backup.isSafeRelativePath(name)) return@readStreaming
                when {
                    name.startsWith(Backup.VOICES_PREFIX) -> {
                        val rest = name.removePrefix(Backup.VOICES_PREFIX)
                        voiceModels.restoreFile(rest.substringBefore('/'), rest.substringAfter('/'), stream)
                    }
                    name == Backup.APP_DATA -> data = AppData.fromJson(stream.readBytes().decodeToString())
                    name == Backup.WORDS -> words = stream.readBytes().decodeToString()
                    name.startsWith("recordings/") -> store.write(file(RECORDING, entryId(name)), stream.readBytes())
                    name.startsWith("voicebank/") -> store.write(file(VOICE, entryId(name)), stream.readBytes())
                }
            }
        }
        val failedVoices = voiceModels.finishRestore()
        val restored = data ?: throw IOException("This backup has no settings in it")
        store.write(WORDS, words.toByteArray())
        store.write(APP_DATA, restored.toJson().toByteArray())
        RestoreResult(restored, words, info, failedVoices)
    }

    /** Reads every entry of a backup, checking it, and counts what it contains. */
    private fun scan(input: InputStream, passphrase: CharArray): BackupInfo {
        var info: BackupInfo? = null
        var hasData = false
        var recordings = 0
        var takes = 0
        val voices = HashSet<String>()
        Backup.readStreaming(input, passphrase) { name, stream ->
            when {
                name == Backup.INFO -> info = BackupInfo.parse(stream.readBytes().decodeToString())
                name == Backup.APP_DATA -> {
                    AppData.fromJson(stream.readBytes().decodeToString())
                    hasData = true
                }
                name.startsWith("recordings/") -> recordings++
                name.startsWith("voicebank/") -> takes++
                name.startsWith(Backup.VOICES_PREFIX) -> voices += name.removePrefix(Backup.VOICES_PREFIX).substringBefore('/')
            }
            stream.skip(Long.MAX_VALUE)
            while (stream.read() >= 0) Unit // read to the end so every chunk is checked
        }
        if (!hasData) throw IOException("This backup has no settings in it")
        return BackupInfo(info?.createdAtMillis ?: 0, recordings, takes, voices.size)
    }

    /** Writes voice banking recordings for training a voice model, encrypted. Returns how many were written. */
    suspend fun exportTrainingData(out: OutputStream, bank: VoiceBank, prompts: List<String>, passphrase: CharArray, now: Long): Int = io {
        var count = 0
        Backup.write(out, passphrase) { w ->
            count = TrainingExport.write(w, bank, prompts, now) { take -> store.read(file(VOICE, take.recordingId)) }
        }
        count
    }

    /** Deletes everything, including the encryption key. */
    suspend fun eraseAll() = io { store.destroyAll() }

    private fun readOrQuarantine(name: String, onProblem: (String) -> Unit): ByteArray? =
        try {
            store.read(name)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt $name", e)
            store.quarantine(name)
            onProblem("Some saved data could not be read and has been set aside.")
            null
        }

    private fun file(prefix: String, id: String) = "$prefix$id.wav"

    private fun ids(prefix: String) = store.list(prefix).map { it.removePrefix(prefix).removeSuffix(".wav") }

    private fun entryId(name: String) = name.substringAfter('/').removeSuffix(".wav")

    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private companion object {
        const val TAG = "Repository"
        const val APP_DATA = "appdata.json"
        const val WORDS = "words.txt"
        const val RECORDING = "rec-"
        const val VOICE = "vb-"
    }
}
