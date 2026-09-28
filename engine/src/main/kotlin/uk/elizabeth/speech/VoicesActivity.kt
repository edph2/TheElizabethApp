// SPDX-License-Identifier: GPL-3.0-or-later
// Piper Voice Engine. Free software under the GNU GPL v3 or later: see engine/LICENSE.
package uk.elizabeth.speech

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The engine's own screen: installed voices (with whose voice each is and their consent),
 * importing voice files, removing voices, and trying a voice. Other apps can open it with
 * ACTION_IMPORT_VOICE and a file to import.
 */
class VoicesActivity : ComponentActivity() {
    private lateinit var models: VoiceModels
    private var tts: TextToSpeech? = null
    private var voices by mutableStateOf(emptyList<InstalledVoice>())
    private var message by mutableStateOf<String?>(null)
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        models = VoiceModels(this)
        refresh()
        if (intent?.action == ACTION_IMPORT_VOICE) incoming = intent.data
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        voices = models.list()
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        var confirmRemove by remember { mutableStateOf<InstalledVoice?>(null) }
        val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickedUri = uri }
        val importUri = pickedUri ?: incoming

        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(getString(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                "A speech engine for Android that speaks with Piper voices, entirely on this device. " +
                    "It has no internet access. Apps use it through Android's standard text-to-speech system.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openFile.launch(arrayOf("*/*")) }) { Text("Import a voice (.elizvoice)") }
                OutlinedButton(onClick = {
                    try {
                        startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    } catch (e: ActivityNotFoundException) {
                        message = "Open Android Settings and search for \"text-to-speech\"."
                    }
                }) { Text("Android speech settings") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (voices.isEmpty()) Text("No voices installed yet.")
            voices.forEach { v ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(v.manifest.name, style = MaterialTheme.typography.titleMedium)
                        Text(describe(v))
                        Text(v.manifest.syntheticVoiceNotice, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { speakSample(v) }) { Text("Try it") }
                            OutlinedButton(onClick = { confirmRemove = v }) { Text("Remove") }
                        }
                    }
                }
            }
            Text(
                "Keep the original .elizvoice files safe: they are the back-up of each voice. " +
                    "This app is free software under the GNU GPL version 3 or later. Source code: ${getString(R.string.source_url)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        importUri?.let { uri ->
            PassphraseDialog(
                onSubmit = { passphrase ->
                    pickedUri = null
                    incoming = null
                    scope.launch {
                        message = "Importing…"
                        val result = withContext(Dispatchers.IO) {
                            runCatching { (contentResolver.openInputStream(uri) ?: error("Could not open the file")).use { models.import(it, passphrase) } }
                        }
                        passphrase.fill(' ')
                        VoiceChanges.changed()
                        refresh()
                        message = result.fold(
                            { "\"${it.manifest.name}\" is installed." },
                            { e -> "Could not import the voice: ${e.message}" },
                        )
                        if (result.isSuccess && intent?.action == ACTION_IMPORT_VOICE) setResult(Activity.RESULT_OK)
                    }
                },
                onDismiss = {
                    pickedUri = null
                    incoming = null
                },
            )
        }
        confirmRemove?.let { v ->
            AlertDialog(
                onDismissRequest = { confirmRemove = null },
                title = { Text("Remove \"${v.manifest.name}\"?") },
                text = { Text("It can be imported again from its .elizvoice file.") },
                confirmButton = {
                    Button(onClick = {
                        models.delete(v.id)
                        VoiceChanges.changed()
                        refresh()
                        confirmRemove = null
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun PassphraseDialog(onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Passphrase for this voice file") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            },
            confirmButton = { Button(enabled = text.length >= 8, onClick = { onSubmit(text.toCharArray()) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }

    private fun describe(v: InstalledVoice): String {
        val m = v.manifest
        val date = m.consent?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.timeMillis)) }
        val language = Locale.forLanguageTag(m.locale).getDisplayName(Locale.UK)
        return when (m.kind) {
            "own" -> "$language. Made from ${m.speakerName}'s own recordings, with consent given $date."
            "donor" -> "$language. Made from ${m.speakerName}'s recordings, with their consent given $date."
            else -> "$language. Published voice (${m.licence})."
        }
    }

    private fun speakSample(v: InstalledVoice) {
        val speak = { engine: TextToSpeech ->
            engine.voices?.firstOrNull { it.name == v.id }?.let { engine.setVoice(it) }
            engine.speak("Hello. This is how this voice sounds.", TextToSpeech.QUEUE_FLUSH, null, "sample")
        }
        val existing = tts
        if (existing != null) speak(existing) else {
            lateinit var created: TextToSpeech
            created = TextToSpeech(this, { status -> if (status == TextToSpeech.SUCCESS) speak(created) }, packageName)
            tts = created
        }
    }

    companion object {
        const val ACTION_IMPORT_VOICE = "uk.elizabeth.speech.action.IMPORT_VOICE"
    }
}

/** Tells Android which languages the installed voices speak (used by Android's speech settings). */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val available = VoiceModels(this).list().map { v ->
            val l = Locale.forLanguageTag(v.manifest.locale)
            runCatching { "${l.isO3Language}-${l.isO3Country}" }.getOrDefault(l.toLanguageTag())
        }.distinct()
        setResult(
            if (available.isEmpty()) TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL else TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
            Intent()
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, ArrayList(available))
                .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf()),
        )
        finish()
    }
}
