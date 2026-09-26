package uk.elizabeth.aac.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.Screen
import uk.elizabeth.aac.UiState
import java.text.DateFormat
import java.util.Date

private enum class Confirm { LEARNED, HISTORY, EVERYTHING }

/** Her data rights, built in: see what is stored, switch learning off, export, and erase. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyScreen(state: UiState, vm: AppViewModel) {
    val data = state.data
    val s = data.settings
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var askExportPassphrase by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var showAllWords by remember { mutableStateOf(false) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri != null && passphrase != null) vm.exportTo(uri, passphrase) else passphrase?.fill(' ')
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri }

    val recordings = data.board.allPhrases().count { it.recordingId != null }
    val logIntact = data.privacyLog.verify()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            CarerHeader("Privacy and data") { vm.openScreen(Screen.SETTINGS) }
            Text(
                "Everything stays on this tablet. The app has no permission to use the internet, " +
                    "does not use cloud backup, and stores everything encrypted.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Section("What the app knows") {
                Text("• ${data.board.allPhrases().size} phrases in ${data.board.categories.size} categories")
                Text("• $recordings phrases recorded in her voice")
                Text("• ${data.history.entries.size} messages in history" + if (!s.historyEnabled) " (history is switched off)" else "")
                Text("• ${state.learnedWords.size} words learned for prediction" + if (!s.learningEnabled) " (learning is switched off)" else "")
                Text("• ${data.privacyLog.events.size} entries in the privacy log")
            }

            Section("Learning") {
                SettingSwitch("Learn words she uses", s.learningEnabled,
                    help = "Makes word prediction personal. Switch off for private conversations.") { v ->
                    vm.updateSettings { it.copy(learningEnabled = v) }
                }
                SettingSwitch("Keep message history", s.historyEnabled, help = "Used for Recent and Say again.") { v ->
                    vm.updateSettings { it.copy(historyEnabled = v) }
                }
                SettingSlider("Messages kept in history", s.historySize.toFloat(), 10f..1000f, 10f, { it.toInt().toString() }) { v ->
                    vm.updateSettings { it.copy(historySize = v.toInt()) }
                }
            }

            Section("Learned words") {
                if (state.learnedWords.isEmpty()) Text("No words learned yet.")
                Text("Press a word to stop it being suggested.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shown = if (showAllWords) state.learnedWords else state.learnedWords.take(60)
                    shown.forEach { (word, count) ->
                        OutlinedButton(onClick = { vm.forgetWord(word) }) { Text("$word ($count) ✕") }
                    }
                }
                if (state.learnedWords.size > 60 && !showAllWords) {
                    Button(onClick = { showAllWords = true }) { Text("Show all ${state.learnedWords.size} words") }
                }
            }

            Section("Export and restore") {
                Text(
                    "The export is one encrypted file containing her phrases, settings, history, learned words " +
                        "and recordings, in open formats. Use it to move to a new tablet or to give her a copy of her data.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { askExportPassphrase = true }) { Text("Export all data") }
                    OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Text("Restore from export") }
                }
            }

            Section("Erase") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { confirm = Confirm.LEARNED }) { Text("Erase learned words") }
                    OutlinedButton(onClick = { confirm = Confirm.HISTORY }) { Text("Erase history") }
                    Button(
                        onClick = { confirm = Confirm.EVERYTHING },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Erase everything") }
                }
            }

            Section("Privacy log") {
                Text(
                    if (logIntact) "✓ The log is intact: no entry has been changed or removed."
                    else "⚠ The log has been altered: some entries were changed or removed.",
                    color = if (logIntact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                data.privacyLog.events.takeLast(50).reversed().forEach { e ->
                    Text("${format.format(Date(e.timeMillis))}  ·  ${e.type.name.lowercase().replace('_', ' ')}")
                }
            }
        }
    }

    when (confirm) {
        Confirm.LEARNED -> ConfirmDialog("Erase learned words?", "Word prediction will go back to the starter vocabulary.", "Erase",
            onConfirm = vm::eraseLearnedWords, onDismiss = { confirm = null })
        Confirm.HISTORY -> ConfirmDialog("Erase history?", "All saved messages will be deleted.", "Erase",
            onConfirm = vm::eraseHistory, onDismiss = { confirm = null })
        Confirm.EVERYTHING -> ConfirmDialog(
            "Erase everything?",
            "This permanently deletes all phrases, recordings of her voice, history, learned words and settings, " +
                "and destroys the encryption key. It cannot be undone. Export first if you might want anything back.",
            "Erase everything",
            onConfirm = vm::eraseEverything, onDismiss = { confirm = null },
        )
        null -> Unit
    }

    if (askExportPassphrase) {
        PassphraseDialog("Choose a passphrase", confirm = true, onSubmit = {
            askExportPassphrase = false
            exportPassphrase = it
            createFile.launch("elizabeth-export.elizbak")
        }, onDismiss = { askExportPassphrase = false })
    }

    importUri?.let { uri ->
        PassphraseDialog("Passphrase for this export", confirm = false, onSubmit = {
            importUri = null
            vm.importFrom(uri, it)
        }, onDismiss = { importUri = null }, message = "Restoring replaces everything currently on this tablet with the contents of the export.")
    }
}
