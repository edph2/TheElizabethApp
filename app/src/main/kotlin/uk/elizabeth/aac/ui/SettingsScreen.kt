package uk.elizabeth.aac.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.Screen
import uk.elizabeth.aac.UiState
import uk.elizabeth.aac.core.keyguard.Keyguard
import uk.elizabeth.aac.core.model.KeyboardLayout
import uk.elizabeth.aac.core.print.PaperBoard
import uk.elizabeth.aac.core.scan.ScanMode
import uk.elizabeth.aac.core.touch.TouchAdvisor
import uk.elizabeth.aac.speech.EngineClient
import uk.elizabeth.aac.speech.EngineVoice
import java.text.DateFormat
import java.util.Date
import uk.elizabeth.aac.core.touch.SelectOn
import uk.elizabeth.aac.speech.SpeakerState

/** Carer settings. Standard Android controls, scrolling allowed. */
@Composable
fun SettingsScreen(state: UiState, speaker: SpeakerState, vm: AppViewModel, controller: TouchController) {
    val s = state.data.settings
    val touch = s.touch
    val context = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var allLanguages by remember { mutableStateOf(false) }
    var deleteVoice by remember { mutableStateOf<String?>(null) }
    // The engine app imports the file: it asks for the passphrase and checks the voice.
    val importVoice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.startActivity(EngineClient(context).importIntent(uri)) }
                .onFailure { vm.showNotice("Could not open the Piper Voice Engine app.") }
        }
    }
    val saveKeyguard = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        if (uri != null) vm.saveKeyguard(uri, keyguardSvg(context, controller))
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            CarerHeader("Settings") { vm.openScreen(Screen.MAIN) }
            BackupReminderCard(state) { vm.openScreen(Screen.PRIVACY) }

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

            Section("Touch suggestions") {
                SettingSwitch(
                    "Count how touches go", s.touchStatsEnabled,
                    help = "Counts only: presses, corrections straight after a press, and near misses. No words or positions. " +
                        "Used to suggest touch settings, which you can accept or ignore.",
                ) { v -> vm.updateSettings { it.copy(touchStatsEnabled = v) } }
                val stats = state.data.touchStats
                if (s.touchStatsEnabled || stats.presses > 0) {
                    Text(
                        "${stats.presses} presses, ${stats.corrections} corrected straight away, ${stats.snapped} near misses matched, " +
                            "${stats.missed} missed, ${stats.tooShort} brushes ignored, ${stats.repeatBlocked} double presses ignored.",
                    )
                    val suggestions = vm.touchSuggestions()
                    when {
                        stats.presses < TouchAdvisor.MIN_PRESSES ->
                            Text("Suggestions appear after ${TouchAdvisor.MIN_PRESSES} presses.", style = MaterialTheme.typography.bodyMedium)
                        suggestions.isEmpty() -> Text("No changes suggested: the current settings seem to suit her.")
                    }
                    suggestions.forEach { suggestion ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(suggestion.change, style = MaterialTheme.typography.titleMedium)
                                Text(suggestion.reason, style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(onClick = { vm.applySuggestion(suggestion) }) { Text("Apply") }
                        }
                    }
                    OutlinedButton(onClick = vm::resetTouchStats) { Text("Reset counts") }
                }
            }

            Section("Switch scanning") {
                Text(
                    "For when touch becomes too difficult. Rows of buttons are highlighted in turn; choosing a row then " +
                        "highlights its buttons. Accessibility switches connect by USB or Bluetooth and act as a keyboard: " +
                        "Enter (or the second switch) chooses, Space (or the first switch) moves on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingSwitch("Use switch scanning", s.scan.enabled) { v -> vm.updateSettings { it.copy(scan = it.scan.copy(enabled = v)) } }
                if (s.scan.enabled) {
                    RadioGroup(
                        listOf(ScanMode.AUTO to "One switch: the highlight moves by itself", ScanMode.STEP to "Two switches: one moves, one chooses"),
                        s.scan.mode,
                    ) { v -> vm.updateSettings { it.copy(scan = it.scan.copy(mode = v)) } }
                    if (s.scan.mode == ScanMode.AUTO) {
                        SettingSlider("Time on each step", s.scan.intervalMs.toFloat(), 400f..6000f, 100f, ::ms) { v ->
                            vm.updateSettings { it.copy(scan = it.scan.copy(intervalMs = v.toLong())) }
                        }
                    }
                    SettingSlider("Passes along a row before going back", s.scan.passesBeforeBack.toFloat(), 1f..5f, 1f, { it.toInt().toString() }) { v ->
                        vm.updateSettings { it.copy(scan = it.scan.copy(passesBeforeBack = v.toInt())) }
                    }
                }
            }

            Section("Keyguard") {
                Text(
                    "A keyguard is a clear acrylic sheet with a hole over each button. She can rest her hand on it without " +
                        "pressing anything, and it guides her finger into the button. This saves a cutting template at real " +
                        "size for a laser-cutting service. Open the main screen once first, and switch on full screen so " +
                        "the buttons do not move.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(enabled = controller.savedLayout.isNotEmpty(), onClick = { saveKeyguard.launch("keyguard.svg") }) {
                    Text("Save keyguard template")
                }
            }

            Section("Layout") {
                SettingSwitch("Full screen", s.fullScreen, help = "Hides the Android status and navigation bars.") { v ->
                    vm.updateSettings { it.copy(fullScreen = v) }
                }
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
                val engine = remember { EngineClient(context) }
                if (!state.engineInstalled) {
                    Text(
                        "Natural voices, including her own, need the free Piper Voice Engine app. Install it from the same " +
                            "place as this app; this app then speaks through it. Until then, the tablet's own speech voice is used.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text("Voices in the Piper Voice Engine", style = MaterialTheme.typography.titleMedium)
                    val selected = if (s.speech.enginePackage == EngineClient.PACKAGE) s.speech.voiceName else null
                    RadioGroup(
                        listOf<Pair<String?, String>>(null to "The tablet's own speech voice (chosen below)") +
                            state.installedVoices.map { v -> v.id to describeVoice(v) },
                        selected,
                    ) { v -> vm.chooseVoice(v) }
                    state.installedVoices.forEach { v ->
                        OutlinedButton(onClick = { deleteVoice = v.id }) { Text("Remove \"${v.name}\"") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { importVoice.launch(arrayOf("*/*")) }) { Text("Import a voice (.elizvoice)") }
                        OutlinedButton(onClick = { engine.openEngineIntent()?.let { context.startActivity(it) } }) { Text("Open the voice engine") }
                    }
                    Text(
                        "Voices run entirely on this tablet, in the Piper Voice Engine app. A voice made from someone's " +
                            "recordings is only accepted with their consent record inside it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = vm::testVoice) { Text("Test voice") }
                Text("Other speech engines and voices", style = MaterialTheme.typography.titleMedium)
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
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            vm.showNotice("This speech engine cannot install voices from here.")
                        }
                    }) { Text("Install voices") }
                }
            }

            Section("Always available") {
                var homeMode by remember { mutableStateOf(HomeScreenMode.isEnabled(context)) }
                SettingSwitch(
                    "Use as the tablet's home screen", homeMode,
                    help = "The app opens when the tablet starts or Home is pressed, and Android brings it straight back if it " +
                        "ever crashes. After switching on, choose this app as the home app. Switch off here to return to normal.",
                ) { v ->
                    runCatching { HomeScreenMode.setEnabled(context, v) }
                        .onFailure { vm.showNotice("Could not open the home screen settings. Choose the home app in Android Settings → Apps.") }
                    homeMode = HomeScreenMode.isEnabled(context)
                }
                Text("Paper board", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A printed board with her phrases and the alphabet, for when the tablet is flat or out of reach. " +
                        "Print it, or save it as a PDF, again whenever the phrases change.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { PaperBoardPrinter.print(context, PaperBoard.html(state.data.board, s.keyboardLayout)) }) {
                    Text("Print paper board")
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

    deleteVoice?.let { id ->
        ConfirmDialog("Remove this voice?", "It can be imported again from its .elizvoice file.", "Remove",
            onConfirm = { vm.deleteVoice(id) }, onDismiss = { deleteVoice = null })
    }

    if (pinDialog) {
        PinDialog("Set carer PIN", onSubmit = { vm.setPin(it); pinDialog = false }, onDismiss = { pinDialog = false })
    }
}

private fun ms(v: Float) = "${v.toInt()} ms"

private fun describeVoice(v: EngineVoice): String {
    val date = v.consentTimeMillis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    return when (v.kind) {
        "own" -> "${v.name}: made from ${v.speakerName}'s own recordings (consent given $date). AI-generated."
        "donor" -> "${v.name}: made from ${v.speakerName}'s recordings, with their consent ($date). AI-generated."
        else -> "${v.name}: published voice (${v.licence}). AI-generated."
    }
}

private fun keyguardSvg(context: Context, controller: TouchController): String {
    val metrics = context.resources.displayMetrics
    val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(real)
        real.widthPixels to real.heightPixels
    }
    return Keyguard.svg(controller.savedLayout, width, height, metrics.xdpi, metrics.ydpi)
}
