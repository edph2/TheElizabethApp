package uk.elizabeth.aac.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a backup file contains. Written into every backup as backup-info.json. */
@Serializable
data class BackupInfo(
    val createdAtMillis: Long,
    val recordings: Int,
    val voiceTakes: Int,
    val voices: Int,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): BackupInfo = json.decodeFromString(serializer(), text)
    }
}

/** Why the carers should make a backup now. */
sealed interface BackupReminder {
    data object NeverBackedUp : BackupReminder
    data class Unsaved(val recordings: Int) : BackupReminder
    data class Old(val days: Long) : BackupReminder
}

/**
 * When her data was last backed up, and which recordings (in her voice) that backup
 * contained. Recordings are irreplaceable once her voice has changed, so any recording not
 * yet in a backup is flagged.
 */
@Serializable
data class BackupStatus(
    val lastBackupMillis: Long = 0,
    val savedRecordingIds: Set<String> = emptySet(),
) {
    fun unsaved(currentRecordingIds: Collection<String>): Int = (currentRecordingIds.toSet() - savedRecordingIds).size

    fun afterBackup(timeMillis: Long, recordingIds: Collection<String>) = BackupStatus(timeMillis, recordingIds.toSet())

    /** A reminder is due if any recording is not backed up, or the last backup is over [maxAgeDays] old. */
    fun reminder(nowMillis: Long, currentRecordingIds: Collection<String>, maxAgeDays: Long = 7): BackupReminder? {
        if (currentRecordingIds.isEmpty()) return null
        if (lastBackupMillis == 0L) return BackupReminder.NeverBackedUp
        val unsaved = unsaved(currentRecordingIds)
        if (unsaved > 0) return BackupReminder.Unsaved(unsaved)
        val days = (nowMillis - lastBackupMillis) / DAY_MS
        return if (days >= maxAgeDays) BackupReminder.Old(days) else null
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
