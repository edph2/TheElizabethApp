package uk.elizabeth.aac.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.PendingRecording
import uk.elizabeth.aac.RecordTarget

/** Returns a function that starts recording, asking for microphone permission first if needed. */
@Composable
fun rememberStartRecording(vm: AppViewModel): (RecordTarget) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<RecordTarget?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = waiting
        waiting = null
        if (granted && target != null) vm.startRecording(target)
        else if (!granted) vm.showNotice("Recording needs permission to use the microphone.")
    }
    return { target ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording(target)
        } else {
            waiting = target
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Shows the quality check for a new recording, and lets the carer keep it, play it or try again. */
@Composable
fun PendingRecordingDialog(pending: PendingRecording, vm: AppViewModel, startRecording: (RecordTarget) -> Unit) {
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
                OutlinedButton(onClick = { vm.discardPending(); startRecording(pending.target) }) { Text("Record again") }
                TextButton(onClick = vm::discardPending) { Text("Discard") }
            }
        },
    )
}
