package uk.elizabeth.aac.ui

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.RecordTarget
import uk.elizabeth.aac.Screen
import uk.elizabeth.aac.UiState
import uk.elizabeth.aac.core.voicebank.VoiceBank
import uk.elizabeth.voiceformat.VoiceConsent

private const val REST_AFTER_MS = 15 * 60 * 1000L

/**
 * Voice banking: she reads sentences aloud, one at a time, so a synthetic voice can later be
 * made from her recordings. Designed for short sessions, because speaking may tire her.
 */
@Composable
fun VoiceBankScreen(state: UiState, vm: AppViewModel) {
    val bank = state.data.voiceBank
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            CarerHeader("Voice banking") { vm.openScreen(Screen.SETTINGS) }
            if (bank.consent == null) ConsentForm(vm) else Recorder(state, bank, vm)
        }
    }
}

@Composable
private fun ConsentForm(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var isSelf by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Voice banking records a voice reading about 300 sentences, so that a synthetic voice can be made from it. " +
                "Record as early as possible, while the voice is at its best. Short sessions of 10 to 15 minutes are fine.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "The recordings are encrypted on this tablet. They only leave it if you export them for training, " +
                "and training should happen on a computer you trust.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Section("Consent") {
            OutlinedTextField(name, { name = it }, label = { Text("Name of the person recording") }, singleLine = true)
            RadioGroup(listOf(true to "Her own voice", false to "A donor's voice (a relative or friend)"), isSelf) { isSelf = it }
            if (name.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(VoiceConsent.statementFor(name.trim(), isSelf), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
                Text("The person recording should read this statement, or have it read to them, and agree to it.")
            }
            Button(enabled = name.isNotBlank(), onClick = { vm.recordVoiceConsent(name, isSelf) }) { Text("They agree. Start voice banking") }
        }
    }
}

@Composable
private fun Recorder(state: UiState, bank: VoiceBank, vm: AppViewModel) {
    val prompts = vm.voicePrompts()
    val index = state.promptIndex.coerceIn(0, (prompts.size - 1).coerceAtLeast(0))
    val take = bank.takeFor(index)
    val recording = state.recordingTarget is RecordTarget.ForPrompt
    val startRecording = rememberStartRecording(vm)
    var autoKeep by remember { mutableStateOf(true) }
    var newPrompt by remember { mutableStateOf("") }
    var confirmWithdraw by remember { mutableStateOf(false) }
    var askPassphrase by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    val sessionStart = remember { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri != null && passphrase != null) vm.exportTrainingData(uri, passphrase) else passphrase?.fill(' ')
    }

    // Keep clear recordings straight away, so reading can flow from one sentence to the next.
    val pending = state.pendingRecording?.takeIf { it.target is RecordTarget.ForPrompt }
    LaunchedEffect(pending) {
        if (pending != null && autoKeep && pending.quality.isGood) vm.keepPending()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val minutes = bank.totalMs / 60_000
        val seconds = (bank.totalMs / 1000) % 60
        Text(
            "${bank.takes.size} of ${prompts.size} sentences recorded · $minutes min ${seconds}s of speech · " +
                "voice of ${bank.consent?.speakerName}",
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(progress = { (bank.totalMs.toFloat() / VoiceBank.GOOD_MS).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(
            when {
                bank.totalMs >= VoiceBank.GOOD_MS -> "Over an hour recorded: enough for a good voice."
                bank.totalMs >= VoiceBank.USABLE_MS -> "Enough for a usable voice. An hour in total gives a noticeably better one."
                else -> "About 20 minutes of speech gives a usable voice; an hour gives a good one."
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (now - sessionStart > REST_AFTER_MS) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(
                    "You've been recording for over 15 minutes. It's a good time to rest: a tired voice is not a true likeness. " +
                        "Everything so far is saved.",
                    Modifier.padding(16.dp),
                )
            }
        }

        Card(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sentence ${index + 1} of ${prompts.size}", style = MaterialTheme.typography.titleMedium)
                Text(
                    prompts.getOrElse(index) { "" },
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                Text(
                    when {
                        recording -> "Recording… read the sentence, then press Stop."
                        take == null -> "Not recorded yet"
                        take.good -> "✓ Recorded (%.1f s)".format(take.durationMs / 1000f)
                        else -> "Recorded, but the quality check found a problem. Consider recording again."
                    },
                    color = if (take != null && !take.good) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (recording) {
                    LinearProgressIndicator(progress = { state.recordingLevel }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("◀ Previous", enabled = !recording) { vm.goToPrompt(index - 1) }
            if (recording) {
                BigButton("■ Stop", primary = true, weight = 2f) { vm.stopRecording() }
            } else {
                BigButton(if (take == null) "● Record" else "● Record again", primary = true, weight = 2f) {
                    startRecording(RecordTarget.ForPrompt(index))
                }
            }
            BigButton("▶ Play", enabled = take != null && !recording) { vm.playTake(index) }
            BigButton("Next ▶", enabled = !recording) { vm.goToPrompt(index + 1) }
            BigButton("Next to record ▶▶", enabled = !recording) { vm.goToNextUnrecorded() }
        }

        SettingSwitch("Keep clear recordings automatically", autoKeep,
            help = "Recordings that pass the quality check are kept and the next sentence appears straight away.") { autoKeep = it }
        if (take != null && !recording) {
            OutlinedButton(onClick = { vm.deleteTake(index) }) { Text("Delete this recording") }
        }

        Section("Tips") {
            Text("• Record in a quiet room with soft furnishings, with the television, radio and fans off.")
            Text("• Keep the tablet (or microphone) at the same distance each time, about an arm's length.")
            Text("• Speak naturally, as if talking to someone, at a comfortable volume. Have water nearby.")
            Text("• Record at the time of day when her voice is strongest.")
        }

        Section("Add her own sentences") {
            Text("Sayings, names and places she uses often make the voice sound more like her.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newPrompt, { newPrompt = it }, label = { Text("Sentence") }, modifier = Modifier.weight(1f))
                Button(enabled = newPrompt.isNotBlank(), onClick = { vm.addVoicePrompt(newPrompt); newPrompt = "" }) { Text("Add") }
            }
        }

        Section("Back up her recordings") {
            Text(
                "These recordings cannot be made again once her voice changes. Back them up at the end of every session.",
                style = MaterialTheme.typography.bodyMedium,
            )
            BackupPanel(state, vm, showCheck = false)
        }

        Section("Make the voice") {
            Text(
                "Export the recordings to train a voice model on a trusted computer (see tools/voice-training in the app's " +
                    "source code). The file is encrypted with a passphrase, includes the consent record, and a checksum of every recording.",
            )
            Button(enabled = bank.takes.isNotEmpty(), onClick = { askPassphrase = true }) { Text("Export recordings for voice training") }
        }

        Section("Withdraw consent") {
            Text("Deletes every voice banking recording on this tablet, and any voice on this tablet made from them. Copies already exported must be deleted separately.")
            Button(
                onClick = { confirmWithdraw = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Withdraw consent and delete recordings") }
        }
    }

    pending?.takeIf { !(autoKeep && it.quality.isGood) }?.let { PendingRecordingDialog(it, vm, startRecording) }

    if (confirmWithdraw) {
        ConfirmDialog(
            "Delete all voice banking recordings?", "This cannot be undone.", "Delete",
            onConfirm = vm::withdrawVoiceConsent, onDismiss = { confirmWithdraw = false },
        )
    }
    if (askPassphrase) {
        PassphraseDialog("Choose a passphrase", confirm = true, onSubmit = {
            askPassphrase = false
            exportPassphrase = it
            createFile.launch("voice-training.elizbak")
        }, onDismiss = { askPassphrase = false })
    }
}

@Composable
private fun RowScope.BigButton(label: String, enabled: Boolean = true, primary: Boolean = false, weight: Float = 1f, onClick: () -> Unit) {
    val modifier = Modifier.weight(weight).fillMaxSize()
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, fontSize = 22.sp, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, fontSize = 18.sp, textAlign = TextAlign.Center) }
    }
}
