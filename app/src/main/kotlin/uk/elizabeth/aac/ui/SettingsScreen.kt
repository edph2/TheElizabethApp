package uk.elizabeth.aac.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.Screen
import uk.elizabeth.aac.UiState
import uk.elizabeth.aac.core.model.KeyboardLayout
import uk.elizabeth.aac.core.touch.SelectOn
import uk.elizabeth.aac.speech.SpeakerState

/** Carer settings. Standard Android controls, scrolling allowed. */
@Composable
fun SettingsScreen(state: UiState, speaker: SpeakerState, vm: AppViewModel) {
    val s = state.data.settings
    val touch = s.touch
    val context = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var allLanguages by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            CarerHeader("Settings") { vm.openScreen(Screen.MAIN) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.openScreen(Screen.PHRASES) }) { Text("Edit phrases and recordings") }
                Button(onClick = { vm.openScreen(Screen.VOICE_BANK) }) { Text("Voice banking") }
                Button(onClick = { vm.openScreen(Screen.PRIVACY) }) { Text("Privacy and data") }
            }

            Section("Touch") {
                Text("How a button is pressed")
                RadioGroup(
                    listOf(
                        SelectOn.RELEASE to "When the finger lifts (recommended: she can slide to the right button first)",
                        SelectOn.DWELL to "By resting on a button (no lift needed)",
                        SelectOn.FIRST_CONTACT to "As soon as the finger touches (fastest, least forgiving)",
                    ),
                    touch.selectOn,
                ) { v -> vm.updateSettings { it.copy(touch = it.touch.copy(selectOn = v)) } }
                if (touch.selectOn == SelectOn.DWELL) {
                    SettingSlider("Resting time", touch.dwellMs.toFloat(), 300f..3000f, 100f, ::ms) { v ->
                        vm.updateSettings { it.copy(touch = it.touch.copy(dwellMs = v.toLong())) }
                    }
                }
                if (touch.selectOn == SelectOn.RELEASE) {
                    SettingSlider("Minimum touch time", touch.minHoldMs.toFloat(), 0f..1000f, 20f, ::ms,
                        help = "Shorter touches are ignored. Increase if brushes or bumps press buttons.") { v ->
                        vm.updateSettings { it.copy(touch = it.touch.copy(minHoldMs = v.toLong())) }
                    }
                    SettingSwitch("Slide to correct", touch.slideToCorrect,
                        help = "On: the button under the finger when it lifts is pressed. Off: only the button first touched.") { v ->
                        vm.updateSettings { it.copy(touch = it.touch.copy(slideToCorrect = v)) }
                    }
                }
                SettingSlider("Ignore repeat presses for", touch.repeatGuardMs.toFloat(), 0f..2000f, 50f, ::ms,
                    help = "Stops a tremor or bounce pressing twice.") { v ->
                    vm.updateSettings { it.copy(touch = it.touch.copy(repeatGuardMs = v.toLong())) }
                }
                SettingSlider("Ignore slips shorter than", touch.slipGraceMs.toFloat(), 0f..500f, 10f, ::ms,
                    help = "A brief slip onto a neighbouring button does not count.") { v ->
                    vm.updateSettings { it.copy(touch = it.touch.copy(slipGraceMs = v.toLong())) }
                }
                SettingSlider("Near-miss distance", touch.snapRadiusDp, 0f..60f, 2f, { "${it.toInt()} dp" },
                    help = "Touches this close to a button count as that button.") { v ->
                    vm.updateSettings { it.copy(touch = it.touch.copy(snapRadiusDp = v)) }
                }
            }

            Section("Layout") {
                SettingSlider("Phrase columns", s.gridColumns.toFloat(), 2f..6f, 1f, { it.toInt().toString() }) { v ->
                    vm.updateSettings { it.copy(gridColumns = v.toInt()) }
                }
                SettingSlider("Phrase rows", s.gridRows.toFloat(), 2f..5f, 1f, { it.toInt().toString() }) { v ->
                    vm.updateSettings { it.copy(gridRows = v.toInt()) }
                }
                SettingSlider("Text size", s.textScale, 0.7f..2.0f, 0.1f, { "${(it * 100).toInt()}%" }) { v ->
                    vm.updateSettings { it.copy(textScale = v) }
                }
                SettingSlider("Word predictions shown", s.predictionCount.toFloat(), 0f..7f, 1f, { it.toInt().toString() }) { v ->
                    vm.updateSettings { it.copy(predictionCount = v.toInt()) }
                }
                Text("Keyboard")
                RadioGroup(listOf(KeyboardLayout.ABC to "Alphabetical (ABC)", KeyboardLayout.QWERTY to "QWERTY"), s.keyboardLayout) { v ->
                    vm.updateSettings { it.copy(keyboardLayout = v) }
                }
                SettingSwitch("High contrast", s.highContrast) { v -> vm.updateSettings { it.copy(highContrast = v) } }
                SettingSwitch("Listener view", s.listenerView, help = "Shows the message upside down at the top, for someone sitting opposite.") { v ->
                    vm.updateSettings { it.copy(listenerView = v) }
                }
            }

            Section("Speaking") {
                SettingSwitch("Speak phrases straight away", s.speakPhrasesImmediately,
                    help = "On: pressing a phrase speaks it. Off: it is added to the message first.") { v ->
                    vm.updateSettings { it.copy(speakPhrasesImmediately = v) }
                }
                SettingSwitch("Clear the message after speaking", s.clearAfterSpeaking) { v ->
                    vm.updateSettings { it.copy(clearAfterSpeaking = v) }
                }
                SettingSwitch("Call sound repeats until stopped", s.attentionRepeats) { v ->
                    vm.updateSettings { it.copy(attentionRepeats = v) }
                }
            }

            Section("Voice") {
                Text("Only voices that work without the internet are listed.", style = MaterialTheme.typography.bodyMedium)
                if (speaker.engines.size > 1) {
                    Text("Speech engine")
                    RadioGroup(listOf<Pair<String?, String>>(null to "System default") + speaker.engines.map { it.packageName to it.label }, s.speech.enginePackage) { v ->
                        vm.updateSettings { it.copy(speech = it.speech.copy(enginePackage = v, voiceName = null)) }
                    }
                }
                speaker.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val voices = if (allLanguages) speaker.voices else speaker.voices.filter { it.label.startsWith("English") }
                Text("Voice")
                RadioGroup(voices.map { it.name to it.label }, speaker.currentVoice) { v ->
                    vm.updateSettings { it.copy(speech = it.speech.copy(voiceName = v)) }
                }
                SettingSwitch("Show voices in all languages", allLanguages) { allLanguages = it }
                SettingSlider("Speed", s.speech.rate, 0.3f..2.5f, 0.05f, { "%.2f×".format(it) }) { v ->
                    vm.updateSettings { it.copy(speech = it.speech.copy(rate = v)) }
                }
                SettingSlider("Pitch", s.speech.pitch, 0.5f..2.0f, 0.05f, { "%.2f×".format(it) }) { v ->
                    vm.updateSettings { it.copy(speech = it.speech.copy(pitch = v)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = vm::testVoice) { Text("Test voice") }
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            vm.showNotice("This speech engine cannot install voices from here.")
                        }
                    }) { Text("Install voices") }
                }
            }

            Section("Carer PIN") {
                Text(
                    if (s.carerPinHash == null) "No PIN. Settings open by holding the Settings button for 2 seconds."
                    else "A PIN is needed to open Settings.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pinDialog = true }) { Text(if (s.carerPinHash == null) "Set PIN" else "Change PIN") }
                    if (s.carerPinHash != null) OutlinedButton(onClick = { vm.setPin(null) }) { Text("Remove PIN") }
                }
            }

            Section("About") {
                Text("The Elizabeth App 0.1.0. This app has no internet access: nothing you say or record leaves this tablet.")
            }
        }
    }

    if (pinDialog) {
        PinDialog("Set carer PIN", onSubmit = { vm.setPin(it); pinDialog = false }, onDismiss = { pinDialog = false })
    }
}

private fun ms(v: Float) = "${v.toInt()} ms"
