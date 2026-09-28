package uk.elizabeth.aac.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.UiState
import uk.elizabeth.voiceformat.Backup
import uk.elizabeth.aac.core.data.BackupReminder
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** The backup reminder, if one is due, in words. */
fun backupReminderText(state: UiState, now: Long = System.currentTimeMillis()): String? =
    when (val r = state.data.backupStatus.reminder(now, state.data.recordingIds())) {
        null -> null
        BackupReminder.NeverBackedUp -> "Her voice recordings have never been backed up. If the tablet is lost or broken, they are gone."
        is BackupReminder.Unsaved -> "${r.recordings} recording(s) of her voice are not in a backup yet."
        is BackupReminder.Old -> "The last backup was ${r.days} days ago."
    }

/** A warning card shown to carers when a backup is due. */
@Composable
fun BackupReminderCard(state: UiState, onBackUp: () -> Unit) {
    val text = backupReminderText(state) ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("⚠ $text", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onBackUp) { Text("Back up now") }
        }
    }
}

/**
 * Backing up: status, "Back up now" (with an option to include installed voices), and
 * "Check a backup". The file goes wherever the carer chooses in Android's save screen: a USB
 * stick, SD card, or a cloud app such as Google Drive. The app itself never uses the network.
 */
@Composable
fun BackupPanel(state: UiState, vm: AppViewModel, showCheck: Boolean = true) {
    var includeVoices by rememberSaveable { mutableStateOf(true) }
    var askPassphrase by remember { mutableStateOf(false) }
    var pendingPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var checkUri by remember { mutableStateOf<Uri?>(null) }
    val status = state.data.backupStatus
    val hasVoices = state.installedVoices.isNotEmpty()

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val passphrase = pendingPassphrase
        pendingPassphrase = null
        if (uri != null && passphrase != null) vm.exportTo(uri, passphrase, includeVoices && hasVoices) else passphrase?.fill(' ')
    }
    val openForCheck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> checkUri = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (status.lastBackupMillis == 0L) "Never backed up."
            else "Last backup: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(status.lastBackupMillis)) + ".",
            style = MaterialTheme.typography.titleMedium,
        )
        backupReminderText(state)?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
        Text(
            "The backup is one encrypted file with everything, including every recording of her voice. Save it " +
                "somewhere other than this tablet: a USB stick, or a cloud app such as Google Drive. The file is " +
                "encrypted before it leaves the app, so the storage company cannot read it. Keeping two copies in " +
                "different places is safest. Write the passphrase down and keep it apart from the file.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (hasVoices) {
            SettingSwitch(
                "Include installed voices", includeVoices,
                help = "Makes the file larger (about 60 to 80 MB per voice), but then one file restores everything.",
            ) { includeVoices = it }
        }
        if (state.backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !state.backupBusy, onClick = { askPassphrase = true }) { Text("Back up now") }
            if (showCheck) {
                OutlinedButton(enabled = !state.backupBusy, onClick = { openForCheck.launch(arrayOf("*/*")) }) { Text("Check a backup") }
            }
        }
    }

    if (askPassphrase) {
        PassphraseDialog("Choose a passphrase", confirm = true, onSubmit = {
            askPassphrase = false
            pendingPassphrase = it
            createFile.launch(Backup.suggestedFileName("elizabeth-backup", LocalDate.now()))
        }, onDismiss = { askPassphrase = false })
    }
    checkUri?.let { uri ->
        PassphraseDialog(
            "Passphrase for this backup", confirm = false,
            message = "The whole file is read and checked. Nothing on this tablet is changed.",
            onSubmit = {
                checkUri = null
                vm.checkBackup(uri, it)
            },
            onDismiss = { checkUri = null },
        )
    }
}
