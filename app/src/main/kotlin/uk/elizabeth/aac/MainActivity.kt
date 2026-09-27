package uk.elizabeth.aac

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.elizabeth.aac.ui.AacButton
import uk.elizabeth.aac.ui.CarerTheme
import uk.elizabeth.aac.ui.Kind
import uk.elizabeth.aac.ui.LocalPalette
import uk.elizabeth.aac.ui.MainScreen
import uk.elizabeth.aac.ui.Palette
import uk.elizabeth.aac.ui.PhraseEditorScreen
import uk.elizabeth.aac.ui.PinDialog
import uk.elizabeth.aac.ui.PrivacyScreen
import uk.elizabeth.aac.ui.SettingsScreen
import uk.elizabeth.aac.ui.TouchController
import uk.elizabeth.aac.ui.TouchSurface
import uk.elizabeth.aac.ui.VoiceBankScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen stays on while the app is open: it is her voice.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { App(vm) }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val speaker by vm.speaker.state.collectAsStateWithLifecycle()
    if (!state.loaded) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Starting…", color = Color.White, fontSize = 28.sp)
        }
        return
    }
    val highContrast = state.data.settings.highContrast
    var askPin by remember { mutableStateOf(false) }
    val controller = remember { TouchController() }
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(state.data.settings.fullScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (state.data.settings.fullScreen) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    var pinError by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalPalette provides if (highContrast) Palette.HighContrast else Palette.Standard) {
        CarerTheme(dark = highContrast) {
            when (state.screen) {
                Screen.MAIN -> MainScreen(state, speaker, vm, controller) {
                    if (vm.needsPin()) askPin = true else vm.openScreen(Screen.SETTINGS)
                }
                Screen.SETTINGS -> SettingsScreen(state, speaker, vm, controller)
                Screen.PHRASES -> PhraseEditorScreen(state, vm)
                Screen.PRIVACY -> PrivacyScreen(state, vm)
                Screen.VOICE_BANK -> VoiceBankScreen(state, vm)
            }
            BackHandler(enabled = state.screen != Screen.MAIN) {
                vm.openScreen(if (state.screen == Screen.SETTINGS) Screen.MAIN else Screen.SETTINGS)
            }

            state.fallbackText?.let { FallbackText(it, vm::dismissFallback) }
            state.notice?.let { Notice(it, vm::dismissNotice) }

            if (askPin) {
                PinDialog("Carer PIN", error = pinError, onSubmit = { pin ->
                    when (val result = vm.checkPin(pin)) {
                        PinResult.Ok -> {
                            askPin = false
                            pinError = null
                            vm.openScreen(Screen.SETTINGS)
                        }
                        PinResult.Wrong -> pinError = "That PIN is not right"
                        is PinResult.Locked -> pinError = "Too many wrong PINs. Try again in ${result.seconds} seconds."
                    }
                }, onDismiss = { askPin = false; pinError = null })
            }
        }
    }
}

/** If speech fails, her message is shown full-screen in large text so it still gets across. */
@Composable
private fun FallbackText(text: String, onClose: () -> Unit) {
    val controller = remember { TouchController() }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        TouchSurface(controller, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text("Speech is not working. Please read:", color = Color.DarkGray, fontSize = 24.sp)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text, color = Color.Black, fontSize = 64.sp, lineHeight = 70.sp, textAlign = TextAlign.Center)
                }
                Box(Modifier.fillMaxWidth().height(120.dp)) {
                    AacButton(controller, "fallback-close", "Close", Kind.CONTROL, onClose, fontSize = 32.sp)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium, shadowElevation = 8.dp) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}
