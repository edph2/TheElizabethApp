package uk.elizabeth.aac.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.aac.core.data.Backup
import uk.elizabeth.aac.core.data.BackupContents

/** Result of loading stored data. [problem] is set if saved data could not be read. */
class LoadResult(val data: AppData, val wordModel: String, val isFirstRun: Boolean, val problem: String?)

/** All reading and writing of her data. Everything goes through [SecureStore], so it is all encrypted. */
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

    suspend fun saveRecording(id: String, wav: ByteArray) = io { store.write(recordingFile(id), wav) }

    suspend fun loadRecording(id: String): ByteArray? = io { runCatching { store.read(recordingFile(id)) }.getOrNull() }

    suspend fun deleteRecording(id: String) = io { store.delete(recordingFile(id)) }

    suspend fun recordingIds(): List<String> = io {
        store.list(RECORDING_PREFIX).map { it.removePrefix(RECORDING_PREFIX).removeSuffix(".wav") }
    }

    /** Builds the encrypted export file. */
    suspend fun exportBackup(data: AppData, wordModel: String, passphrase: CharArray): ByteArray = io {
        val recordings = store.list(RECORDING_PREFIX).associate { file ->
            file.removePrefix(RECORDING_PREFIX).removeSuffix(".wav") to (store.read(file) ?: ByteArray(0))
        }
        Backup.encrypt(Backup.zip(BackupContents(data.toJson(), wordModel, recordings)), passphrase)
    }

    /** Decrypts and unpacks an export file. Nothing is changed until [restore] is called. */
    suspend fun openBackup(bytes: ByteArray, passphrase: CharArray): BackupContents = io {
        Backup.unzip(Backup.decrypt(bytes, passphrase))
    }

    /** Replaces all stored data with the contents of an export. */
    suspend fun restore(contents: BackupContents, data: AppData) = io {
        store.list(RECORDING_PREFIX).forEach { store.delete(it) }
        contents.recordings.forEach { (id, wav) -> store.write(recordingFile(id), wav) }
        store.write(WORDS, contents.wordModel.toByteArray())
        store.write(APP_DATA, data.toJson().toByteArray())
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

    private fun recordingFile(id: String) = "$RECORDING_PREFIX$id.wav"

    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private companion object {
        const val TAG = "Repository"
        const val APP_DATA = "appdata.json"
        const val WORDS = "words.txt"
        const val RECORDING_PREFIX = "rec-"
    }
}
