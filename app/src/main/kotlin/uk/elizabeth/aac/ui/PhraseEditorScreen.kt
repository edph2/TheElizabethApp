package uk.elizabeth.aac.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.Screen
import uk.elizabeth.aac.UiState
import uk.elizabeth.aac.core.model.Phrase

private const val QUICK = "quick-replies"

/**
 * Edit phrases and categories, and record phrases in her own voice (message banking).
 * A recorded phrase is played as her real recording, not the synthetic voice.
 */
@Composable
fun PhraseEditorScreen(state: UiState, vm: AppViewModel) {
    val board = state.data.board
    var selected by remember { mutableStateOf(board.categories.firstOrNull()?.id ?: QUICK) }
    var newPhrase by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var renameTo by remember(selected) { mutableStateOf(board.categories.firstOrNull { it.id == selected }?.label ?: "") }
    var confirmDeleteCategory by remember { mutableStateOf(false) }
    var recordFor by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = recordFor
        if (granted && id != null) vm.startRecording(id)
        else if (!granted) vm.showNotice("Recording needs permission to use the microphone.")
    }
    fun record(phraseId: String) {
        recordFor = phraseId
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording(phraseId)
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val category = board.categories.firstOrNull { it.id == selected }
    val phrases = if (selected == QUICK) board.quickReplies else category?.phrases ?: emptyList()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp)) {
            CarerHeader("Phrases and recordings") { vm.openScreen(Screen.SETTINGS) }
            Text(
                "Record phrases in her own voice while she can: they are played exactly as recorded. " +
                    "Recordings are encrypted and never leave the tablet unless you export them.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(Modifier.weight(0.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        CategoryButton("Quick replies", selected == QUICK) { selected = QUICK }
                    }
                    items(board.categories, key = { it.id }) { c ->
                        CategoryButton(c.label, selected == c.id) { selected = c.id }
                    }
                    item {
                        Column(Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(newCategory, { newCategory = it }, label = { Text("New category") }, singleLine = true)
                            Button(enabled = newCategory.isNotBlank(), onClick = {
                                vm.editBoard { it.addCategory(newCategory) }
                                newCategory = ""
                            }) { Text("Add category") }
                        }
                    }
                }
                LazyColumn(Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (category != null) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(renameTo, { renameTo = it }, label = { Text("Category name") }, singleLine = true)
                                OutlinedButton(onClick = { vm.editBoard { it.renameCategory(category.id, renameTo) } }) { Text("Rename") }
                                OutlinedButton(onClick = { confirmDeleteCategory = true }) { Text("Delete category") }
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(newPhrase, { newPhrase = it }, label = { Text("New phrase") }, modifier = Modifier.weight(1f))
                                Button(enabled = newPhrase.isNotBlank(), onClick = {
                                    vm.editBoard { it.addPhrase(category.id, newPhrase) }
                                    newPhrase = ""
                                }) { Text("Add") }
                            }
                        }
                    }
                    items(phrases, key = { it.id }) { phrase ->
                        PhraseRow(phrase, state, canEdit = selected != QUICK, vm = vm, onRecord = { record(phrase.id) })
                    }
                }
            }
        }
    }

    if (confirmDeleteCategory && category != null) {
        ConfirmDialog(
            "Delete \"${category.label}\"?",
            "This deletes the category, its ${category.phrases.size} phrases and any recordings of them.",
            "Delete",
            onConfirm = {
                vm.editBoard { it.removeCategory(category.id) }
                selected = QUICK
            },
            onDismiss = { confirmDeleteCategory = false },
        )
    }

    state.pendingRecording?.let { pending ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Keep this recording?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Length: %.1f seconds".format(pending.audio.durationMs / 1000f))
                    if (pending.quality.isGood) Text("The recording sounds clear.")
                    pending.quality.issues.forEach { Text("• ${it.advice}", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = vm::keepPending) { Text("Keep") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::playPending) { Text("Play") }
                    OutlinedButton(onClick = { vm.discardPending(); record(pending.phraseId) }) { Text("Record again") }
                    TextButton(onClick = vm::discardPending) { Text("Discard") }
                }
            },
        )
    }
}

@Composable
private fun CategoryButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun PhraseRow(phrase: Phrase, state: UiState, canEdit: Boolean, vm: AppViewModel, onRecord: () -> Unit) {
    val recordingThis = state.recordingPhraseId == phrase.id
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (phrase.recordingId != null) "🎙 " else "") + phrase.text,
                style = MaterialTheme.typography.titleMedium,
            )
            if (recordingThis) {
                Text("Recording… say the phrase, then press Stop.")
                LinearProgressIndicator(progress = { state.recordingLevel }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recordingThis) {
                    Button(onClick = vm::stopRecording) { Text("Stop") }
                } else {
                    OutlinedButton(enabled = state.recordingPhraseId == null, onClick = onRecord) {
                        Text(if (phrase.recordingId == null) "Record her voice" else "Re-record")
                    }
                }
                if (phrase.recordingId != null) {
                    OutlinedButton(onClick = { vm.playRecording(phrase) }) { Text("Play") }
                    OutlinedButton(onClick = { vm.removeRecording(phrase.id) }) { Text("Remove recording") }
                }
                if (canEdit) {
                    OutlinedButton(onClick = { vm.editBoard { it.movePhrase(phrase.id, -1) } }) { Text("▲") }
                    OutlinedButton(onClick = { vm.editBoard { it.movePhrase(phrase.id, 1) } }) { Text("▼") }
                    OutlinedButton(onClick = { vm.editBoard { it.removePhrase(phrase.id) } }) { Text("Delete") }
                }
            }
        }
    }
}
