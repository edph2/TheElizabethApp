package uk.elizabeth.aac.ui

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.elizabeth.aac.Tabs
import uk.elizabeth.aac.UiState
import uk.elizabeth.aac.AppViewModel
import uk.elizabeth.aac.core.model.Paging
import uk.elizabeth.aac.core.model.Phrase
import uk.elizabeth.aac.speech.SpeakerState

private const val TABS_PER_PAGE = 6

private val chooseKeys = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonA, Key.Two)
private val moveKeys = setOf(Key.Spacebar, Key.Tab, Key.DirectionRight, Key.ButtonB, Key.One)

/** The communication screen she uses all day. Nothing on it scrolls. */
@Composable
fun MainScreen(state: UiState, speaker: SpeakerState, vm: AppViewModel, controller: TouchController, onOpenSettings: () -> Unit) {
    val palette = LocalPalette.current
    val settings = state.data.settings
    controller.applySettings(settings.touch, settings.scan)
    controller.onTouchEvent = if (settings.touchStatsEnabled) vm::recordTouch else null
    val scale = settings.textScale
    val focus = remember { FocusRequester() }
    val view = LocalView.current

    if (settings.scan.enabled) {
        LaunchedEffect(settings.scan) {
            focus.requestFocus()
            while (true) {
                controller.scanTick(SystemClock.uptimeMillis())
                delay(50)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .onGloballyPositioned {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                controller.screenOffset = Offset(location[0].toFloat(), location[1].toFloat())
            }
            .onPreviewKeyEvent { event ->
                // Accessibility switches connect as a keyboard. Space/Tab move, Enter chooses;
                // with one-switch scanning any of them chooses.
                if (!settings.scan.enabled) return@onPreviewKeyEvent false
                val choose = event.key in chooseKeys
                val move = event.key in moveKeys
                if (!choose && !move) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) controller.onSwitch(choose, SystemClock.uptimeMillis())
                true
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        StatusStrip(speaker, state.customSpeaking) {
            controller.saveLayout() // for the keyguard template, which is made from Settings
            onOpenSettings()
        }
        if (settings.listenerView) {
            Text(
                text = state.message.ifBlank { " " },
                color = palette.messageText,
                fontSize = (30 * scale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.messageBackground)
                    .rotate(180f)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        TouchSurface(controller, Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().padding(4.dp)) {
                MessageBar(state.message, scale, Modifier.weight(1.3f))
                Row(Modifier.weight(1.1f)) {
                    Cell(2f) { AacButton(controller, "speak", "SPEAK", Kind.SPEAK, vm::onSpeak, fontSize = (30 * scale).sp) }
                    Cell { AacButton(controller, "undo", "Undo", Kind.CONTROL, vm::onUndo, fontSize = (22 * scale).sp) }
                    Cell { AacButton(controller, "delete-word", "Delete word", Kind.CONTROL, vm::onDeleteWord, fontSize = (20 * scale).sp) }
                    Cell { AacButton(controller, "clear", "Clear", Kind.CONTROL, vm::onClear, fontSize = (22 * scale).sp) }
                    Cell { AacButton(controller, "repeat", "Say again", Kind.CONTROL, vm::onRepeat, fontSize = (20 * scale).sp) }
                    Cell {
                        AacButton(
                            controller, "attention", if (state.attentionOn) "Stop sound" else "🔔 Call", Kind.ATTENTION,
                            vm::onAttention, fontSize = (22 * scale).sp,
                        )
                    }
                }
                PredictionRow(state, controller, vm, scale, Modifier.weight(1f))
                Row(Modifier.weight(5f)) {
                    Column(Modifier.weight(0.17f).fillMaxHeight()) {
                        state.data.board.quickReplies.forEach { phrase ->
                            val kind = when (phrase.id) {
                                "quick-yes" -> Kind.QUICK_YES
                                "quick-no" -> Kind.QUICK_NO
                                else -> Kind.QUICK
                            }
                            Box(Modifier.weight(1f)) {
                                AacButton(
                                    controller, "quick-${phrase.id}", phrase.text, kind, { vm.onQuickReply(phrase) },
                                    fontSize = (26 * scale).sp, badge = recordedBadge(phrase),
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(0.83f).fillMaxHeight()) {
                        TabRow(state, controller, vm, scale, Modifier.weight(1f))
                        Box(Modifier.weight(4.2f)) {
                            when (state.tab) {
                                Tabs.KEYBOARD -> Keyboard(state, controller, vm, scale)
                                Tabs.RECENT -> RecentGrid(state, controller, vm, scale)
                                else -> PhraseGrid(state, controller, vm, scale)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Cell(weight: Float = 1f, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight).fillMaxHeight()) { content() }
}

private fun recordedBadge(phrase: Phrase) = if (phrase.recordingId != null) "🎙" else null

@Composable
private fun MessageBar(message: String, scale: Float, modifier: Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(palette.messageBackground, RoundedCornerShape(12.dp))
            .border(2.dp, palette.highlight, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "Message: $message" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (message.isEmpty()) "…" else "$message▏",
            color = palette.messageText,
            fontSize = (34 * scale).sp,
            lineHeight = (38 * scale).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PredictionRow(state: UiState, controller: TouchController, vm: AppViewModel, scale: Float, modifier: Modifier) {
    val slots = state.data.settings.predictionCount
    Row(modifier.fillMaxWidth()) {
        state.messageSuggestions.forEachIndexed { i, text ->
            Cell(2f) { AacButton(controller, "suggestion-$i", text, Kind.SUGGESTION, { vm.onMessageSuggestion(text) }, fontSize = (18 * scale).sp) }
        }
        for (i in 0 until slots) {
            val word = state.predictions.getOrNull(i)
            Cell {
                AacButton(
                    controller, "prediction-$i", word ?: "", Kind.PREDICTION, { if (word != null) vm.onWord(word) },
                    fontSize = (24 * scale).sp, enabled = word != null,
                )
            }
        }
    }
}

@Composable
private fun TabRow(state: UiState, controller: TouchController, vm: AppViewModel, scale: Float, modifier: Modifier) {
    val tabs = state.data.board.categories.map { it.id to it.label } + listOf(Tabs.RECENT to "Recent", Tabs.KEYBOARD to "ABC")
    val pages = Paging.pageCount(tabs.size, TABS_PER_PAGE)
    val tabPage = state.tabPage.coerceIn(0, pages - 1)
    Row(modifier.fillMaxWidth()) {
        if (pages > 1) Cell(0.6f) { AacButton(controller, "tabs-prev", "◀", Kind.NAV, { vm.changeTabPage(-1) }, fontSize = 26.sp) }
        Paging.page(tabs, tabPage, TABS_PER_PAGE).forEach { (id, label) ->
            Cell {
                AacButton(
                    controller, "tab-$id", label, if (state.tab == id) Kind.TAB_SELECTED else Kind.TAB,
                    { vm.selectTab(id) }, fontSize = (20 * scale).sp,
                )
            }
        }
        if (pages > 1) Cell(0.6f) { AacButton(controller, "tabs-next", "▶", Kind.NAV, { vm.changeTabPage(1) }, fontSize = 26.sp) }
    }
}

@Composable
private fun PhraseGrid(state: UiState, controller: TouchController, vm: AppViewModel, scale: Float) {
    val phrases = state.data.board.categories.firstOrNull { it.id == state.tab }?.phrases ?: emptyList()
    PagedGrid(state, controller, vm, scale, phrases.map { p -> GridItem("phrase-${p.id}", p.text, recordedBadge(p)) { vm.onPhrase(p) } })
}

@Composable
private fun RecentGrid(state: UiState, controller: TouchController, vm: AppViewModel, scale: Float) {
    val recent = state.data.history.recent(60)
    if (recent.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Messages you say will appear here", color = LocalPalette.current.statusText, fontSize = 22.sp)
        }
        return
    }
    PagedGrid(state, controller, vm, scale, recent.mapIndexed { i, text -> GridItem("recent-$i", text, null) { vm.onRecent(text) } })
}

private class GridItem(val id: String, val text: String, val badge: String?, val onPress: () -> Unit)

@Composable
private fun PagedGrid(state: UiState, controller: TouchController, vm: AppViewModel, scale: Float, items: List<GridItem>) {
    val settings = state.data.settings
    val columns = settings.gridColumns
    val perPage = settings.phrasesPerPage
    val pages = Paging.pageCount(items.size, perPage)
    val page = state.page.coerceIn(0, pages - 1)
    val visible = Paging.page(items, page, perPage)
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until settings.gridRows) {
            Row(Modifier.weight(1f)) {
                for (col in 0 until columns) {
                    val item = visible.getOrNull(row * columns + col)
                    Cell {
                        if (item != null) {
                            AacButton(controller, item.id, item.text, Kind.PHRASE, item.onPress, fontSize = (22 * scale).sp, badge = item.badge)
                        } else {
                            AacButton(controller, "cell-$row-$col", "", Kind.PHRASE, {}, enabled = false)
                        }
                    }
                }
            }
        }
        // Always shown, so buttons never change size or move between categories (motor memory, keyguard).
        run {
            Row(Modifier.weight(0.7f)) {
                Cell { AacButton(controller, "page-prev", "◀ Back", Kind.NAV, { vm.changePage(-1) }, fontSize = 22.sp, enabled = page > 0) }
                Cell {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Page ${page + 1} of $pages", color = LocalPalette.current.statusText, fontSize = 20.sp)
                    }
                }
                Cell { AacButton(controller, "page-next", "Next ▶", Kind.NAV, { vm.changePage(1) }, fontSize = 22.sp, enabled = page < pages - 1) }
            }
        }
    }
}

@Composable
private fun Keyboard(state: UiState, controller: TouchController, vm: AppViewModel, scale: Float) {
    val rows = if (state.symbols) listOf("12345", "67890", ".,?!'", "-£&@:") else state.data.settings.keyboardLayout.rows
    val keyFont = (28 * scale).sp
    Column(Modifier.fillMaxSize()) {
        rows.forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { c ->
                    Cell { AacButton(controller, "key-$c", c.toString(), Kind.KEY, { vm.onKey(c) }, fontSize = keyFont) }
                }
            }
        }
        Row(Modifier.weight(1f)) {
            Cell(1.2f) { AacButton(controller, "key-symbols", if (state.symbols) "abc" else "123", Kind.CONTROL, vm::toggleSymbols, fontSize = (22 * scale).sp) }
            Cell(3f) { AacButton(controller, "key-space", "space", Kind.KEY, { vm.onKey(' ') }, fontSize = (22 * scale).sp) }
            Cell { AacButton(controller, "key-full-stop", ".", Kind.KEY, { vm.onKey('.') }, fontSize = keyFont) }
            Cell(1.5f) { AacButton(controller, "key-backspace", "⌫", Kind.CONTROL, vm::onBackspace, fontSize = keyFont) }
        }
    }
}

/**
 * The thin strip at the top: warnings (volume, battery, speech) and the settings button,
 * which must be held for 2 seconds so it is never opened by accident.
 */
@Composable
private fun StatusStrip(speaker: SpeakerState, customSpeaking: Boolean, onOpenSettings: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(speaker.problem) {
        while (true) {
            warnings = deviceWarnings(context) + listOfNotNull(speaker.problem)
            delay(3_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (speaker.speaking || customSpeaking) "🔊 Speaking…" else "", color = palette.statusText, fontSize = 18.sp, modifier = Modifier.width(150.dp))
        Text(
            warnings.joinToString("   ·   ") { "⚠ $it" },
            color = palette.highlight,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HoldToOpen("⚙ Settings (hold)", onOpenSettings)
    }
}

@Composable
private fun HoldToOpen(label: String, onOpen: () -> Unit, holdMs: Long = 2_000) {
    val palette = LocalPalette.current
    var progress by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxHeight()
            .background(palette.background(Kind.NAV), RoundedCornerShape(8.dp))
            .drawBehind { drawRect(palette.dwell, size = size.copy(width = size.width * progress)) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val start = SystemClock.uptimeMillis()
                    while (true) {
                        val event = withTimeoutOrNull(50L) { awaitPointerEvent() }
                        if (event != null && event.changes.none { it.pressed }) break
                        progress = ((SystemClock.uptimeMillis() - start).toFloat() / holdMs).coerceIn(0f, 1f)
                        if (progress >= 1f) {
                            onOpen()
                            break
                        }
                    }
                    progress = 0f
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = palette.content(Kind.NAV), fontSize = 16.sp)
    }
}

private fun deviceWarnings(context: Context): List<String> {
    val warnings = mutableListOf<String>()
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    if (volume == 0) warnings += "Sound is off" else if (volume * 10 < max * 4) warnings += "Volume is low"
    val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (level in 0..20 && !battery.isCharging) warnings += "Battery $level% — please charge"
    return warnings
}
