package uk.elizabeth.aac.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.elizabeth.aac.core.scan.ScanMode
import uk.elizabeth.aac.core.scan.ScanSettings
import uk.elizabeth.aac.core.scan.Scanner
import uk.elizabeth.aac.core.touch.Bounds
import uk.elizabeth.aac.core.touch.TargetRegistry
import uk.elizabeth.aac.core.touch.TouchEvent
import uk.elizabeth.aac.core.touch.TouchFilter
import uk.elizabeth.aac.core.touch.TouchSettings

/**
 * Connects the on-screen buttons to the [TouchFilter]. Buttons register where they are;
 * the [TouchSurface] sends every finger movement here, and this decides which button
 * (if any) was deliberately pressed.
 */
class TouchController {
    private val filter = TouchFilter()
    private val registry = TargetRegistry()
    private val actions = HashMap<String, () -> Unit>()

    private val scanner = Scanner()
    private var scanEnabled = false
    private var lastSwitchAt = Long.MIN_VALUE / 2

    var highlighted by mutableStateOf<String?>(null)
        private set
    var dwellProgress by mutableFloatStateOf(0f)
        private set

    /** Buttons highlighted by switch scanning. */
    var scanHighlight by mutableStateOf<List<String>>(emptyList())
        private set

    internal var origin = Offset.Zero
    internal var density = 1f

    /** Where the touch layer is on the physical screen, for the keyguard template. */
    var screenOffset = Offset.Zero
        internal set

    fun applySettings(settings: TouchSettings, scan: ScanSettings = ScanSettings()) {
        filter.settings = settings
        scanner.settings = scan
        if (scanEnabled != scan.enabled) {
            scanEnabled = scan.enabled
            scanner.restart(SystemClock.uptimeMillis())
            if (!scan.enabled) scanHighlight = emptyList()
        }
    }

    /** The main screen's buttons, in pixels on the physical screen, as they were when Settings was opened. */
    var savedLayout: Map<String, Bounds> = emptyMap()
        private set

    fun saveLayout() {
        savedLayout = (registry.snapshot() + placeholders).mapValues { (_, b) ->
            Bounds(b.left + screenOffset.x, b.top + screenOffset.y, b.right + screenOffset.x, b.bottom + screenOffset.y)
        }
    }

    /** Advances switch scanning; call regularly while scanning is on. */
    internal fun scanTick(time: Long) {
        if (!scanEnabled) return
        scanner.setRows(Scanner.rowsOf(registry.snapshot()), time)
        scanner.tick(time)
        scanHighlight = scanner.highlighted
    }

    /** A switch was pressed. [choose] is false for the "move" switch in two-switch scanning. */
    internal fun onSwitch(choose: Boolean, time: Long) {
        if (!scanEnabled) return
        if (time - lastSwitchAt < filter.settings.repeatGuardMs) return
        lastSwitchAt = time
        if (!choose && scanner.settings.mode == ScanMode.STEP) {
            scanner.move(time)
        } else {
            scanner.choose(time)?.let { actions[it]?.invoke() }
        }
        scanHighlight = scanner.highlighted
    }

    internal fun register(id: String, rect: Rect, action: () -> Unit) {
        registry.register(id, Bounds(rect.left, rect.top, rect.right, rect.bottom))
        actions[id] = action
    }

    internal fun unregister(id: String) {
        registry.unregister(id)
        actions.remove(id)
        placeholders.remove(id)
    }

    /** Empty cells: not pressable or scanned, but they get a hole in the keyguard. */
    private val placeholders = HashMap<String, Bounds>()

    internal fun registerPlaceholder(id: String, rect: Rect) {
        registry.unregister(id)
        actions.remove(id)
        placeholders[id] = Bounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    /** Receives touch statistics events (counts only), if set. */
    var onTouchEvent: ((TouchEvent) -> Unit)? = null
    private var lastPress: Pair<String, Long>? = null

    internal fun down(position: Offset, time: Long) {
        val target = hit(position)
        onTouchEvent?.let { report ->
            when {
                target == null -> report(TouchEvent.MISSED)
                registry.hitTest(position.x + origin.x, position.y + origin.y, 0f) == null -> report(TouchEvent.SNAPPED)
            }
        }
        handle(filter.onDown(target, time), time)
    }

    internal fun move(position: Offset, time: Long) = handle(filter.onMove(hit(position), time), time)
    internal fun up(position: Offset, time: Long) = handle(filter.onUp(hit(position), time), time)
    internal fun tick(time: Long) = handle(filter.tick(time), time)

    internal fun cancel() {
        filter.onCancel()
        highlighted = null
        dwellProgress = 0f
    }

    private fun hit(p: Offset): String? =
        registry.hitTest(p.x + origin.x, p.y + origin.y, filter.settings.snapRadiusDp * density)

    private fun handle(pressed: String?, time: Long) {
        highlighted = filter.activeTarget
        dwellProgress = filter.dwellProgress(time)
        onTouchEvent?.let { report ->
            when (filter.lastOutcome) {
                TouchFilter.Outcome.TOO_SHORT -> report(TouchEvent.TOO_SHORT)
                TouchFilter.Outcome.REPEAT_BLOCKED -> report(TouchEvent.REPEAT_BLOCKED)
                else -> Unit
            }
            if (pressed != null) {
                report(TouchEvent.PRESSED)
                val previous = lastPress
                if (pressed in correctionButtons && previous != null && previous.first !in correctionButtons && time - previous.second < 3_000) {
                    report(TouchEvent.CORRECTION)
                }
                lastPress = pressed to time
            }
        }
        if (pressed != null) actions[pressed]?.invoke()
    }

    private companion object {
        val correctionButtons = setOf("undo", "delete-word", "clear", "key-backspace")
    }
}

/**
 * One touch layer over all the buttons inside it. Only the first finger is followed, so a
 * resting palm or wrist is ignored. Nothing here needs swiping, scrolling or long-pressing.
 */
@Composable
fun TouchSurface(controller: TouchController, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    controller.density = LocalDensity.current.density
    Box(
        modifier
            .onGloballyPositioned { controller.origin = it.positionInRoot() }
            .pointerInput(controller) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    first.consume()
                    controller.down(first.position, SystemClock.uptimeMillis())
                    while (true) {
                        // Wake regularly even without movement, so dwell selection can complete.
                        val event = withTimeoutOrNull(40L) { awaitPointerEvent(PointerEventPass.Initial) }
                        if (event == null) {
                            controller.tick(SystemClock.uptimeMillis())
                            continue
                        }
                        event.changes.forEach { it.consume() }
                        val change = event.changes.firstOrNull { it.id == first.id }
                        if (change == null) {
                            controller.cancel()
                            break
                        }
                        if (!change.pressed) {
                            controller.up(change.position, SystemClock.uptimeMillis())
                            break
                        }
                        controller.move(change.position, SystemClock.uptimeMillis())
                    }
                }
            },
        content = content,
    )
}

/**
 * A communication button. It is pressed through the [TouchSurface], and also works with
 * TalkBack and Switch Access through its accessibility action.
 */
@Composable
fun AacButton(
    controller: TouchController,
    id: String,
    text: String,
    kind: Kind,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    badge: String? = null,
    /** False for an empty cell: drawn as an outline, cannot be pressed, keeps its place in the keyguard. */
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val action by rememberUpdatedState(onPress)
    DisposableEffect(controller, id) { onDispose { controller.unregister(id) } }
    val highlighted = controller.highlighted == id || id in controller.scanHighlight
    val shape = remember { RoundedCornerShape(14.dp) }
    if (!enabled) {
        Box(
            modifier
                .fillMaxSize()
                .padding(4.dp)
                .onGloballyPositioned { controller.registerPlaceholder(id, it.boundsInRoot()) }
                .border(1.dp, palette.background(kind), shape),
        )
        return
    }
    Box(
        modifier
            .fillMaxSize()
            .padding(4.dp)
            .onGloballyPositioned { controller.register(id, it.boundsInRoot()) { action() } }
            .semantics {
                role = Role.Button
                contentDescription = text
                onClick { action(); true }
            }
            .clip(shape)
            .background(palette.background(kind))
            .then(if (highlighted) Modifier.border(6.dp, palette.highlight, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (highlighted && controller.dwellProgress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(controller.dwellProgress)
                    .background(palette.dwell),
            )
        }
        Text(
            text = text,
            color = palette.content(kind),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = fontSize * 1.1f,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
        if (badge != null) {
            Text(badge, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}
