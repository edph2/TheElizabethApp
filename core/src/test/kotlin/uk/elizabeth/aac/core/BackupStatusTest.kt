package uk.elizabeth.aac.core

import uk.elizabeth.aac.core.data.AppData
import uk.elizabeth.voiceformat.Backup
import uk.elizabeth.aac.core.data.BackupInfo
import uk.elizabeth.aac.core.data.BackupReminder
import uk.elizabeth.aac.core.data.BackupStatus
import uk.elizabeth.aac.core.voicebank.VoiceBank
import uk.elizabeth.aac.core.voicebank.VoiceTake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupStatusTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `reminders for never, unsaved and old backups`() {
        val ids = setOf("a", "b")
        assertNull(BackupStatus().reminder(0, emptySet()))
        assertEquals(BackupReminder.NeverBackedUp, BackupStatus().reminder(0, ids))
        val saved = BackupStatus().afterBackup(10 * day, setOf("a"))
        assertEquals(BackupReminder.Unsaved(1), saved.reminder(10 * day, ids))
        val all = saved.afterBackup(10 * day, ids)
        assertNull(all.reminder(16 * day, ids))
        assertEquals(BackupReminder.Old(7), all.reminder(17 * day, ids))
    }

    @Test
    fun `recording ids cover phrases and voice banking`() {
        val board = AppData().board.let { b -> b.updatePhrase(b.allPhrases().first().id) { it.copy(recordingId = "p1") } }
        val data = AppData(board = board, voiceBank = VoiceBank().withTake(VoiceTake(0, "v1", 1, true, 0)))
        assertEquals(setOf("p1", "v1"), data.recordingIds())
    }

    @Test
    fun `file names sort by date`() {
        assertEquals("elizabeth-backup-2026-09-27.elizbak", Backup.suggestedFileName("elizabeth-backup", LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun `large files stream into a backup and nested names are allowed`() {
        val out = ByteArrayOutputStream()
        val big = ByteArray(3_000_000) { (it % 251).toByte() }
        Backup.write(out, "correct horse".toCharArray()) { w ->
            w.put(Backup.INFO, BackupInfo(5, 1, 2, 1).toJson())
            w.put(Backup.voicePackageEntry("id1"), ByteArrayInputStream(big))
            w.put("voices/nested/espeak-ng-data/voices/!v/Mr serious", ByteArrayInputStream(big))
        }
        val seen = HashMap<String, Int>()
        Backup.readStreaming(ByteArrayInputStream(out.toByteArray()), "correct horse".toCharArray()) { name, data ->
            seen[name] = data.readBytes().size
        }
        assertEquals(3_000_000, seen["voices/id1.zip"])
        assertEquals(3_000_000, seen["voices/nested/espeak-ng-data/voices/!v/Mr serious"])
        assertEquals(BackupInfo(5, 1, 2, 1), BackupInfo.parse(BackupInfo(5, 1, 2, 1).toJson()))
    }
}
