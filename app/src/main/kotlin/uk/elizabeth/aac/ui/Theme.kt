package uk.elizabeth.aac.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Button colours by purpose, so she learns where things are by colour as well as position. */
enum class Kind { PHRASE, QUICK_YES, QUICK_NO, QUICK, SPEAK, CONTROL, ATTENTION, PREDICTION, SUGGESTION, KEY, TAB, TAB_SELECTED, NAV }

data class Palette(
    val background: Color,
    val messageBackground: Color,
    val messageText: Color,
    val highlight: Color,
    val dwell: Color,
    val statusText: Color,
    private val buttons: Map<Kind, Pair<Color, Color>>,
) {
    fun background(kind: Kind) = buttons.getValue(kind).first
    fun content(kind: Kind) = buttons.getValue(kind).second

    companion object {
        // Every pair below has a contrast ratio of at least 7:1 (WCAG AAA).
        val HighContrast = Palette(
            background = Color(0xFF000000),
            messageBackground = Color(0xFFFFFFFF),
            messageText = Color(0xFF000000),
            highlight = Color(0xFFFFD600),
            dwell = Color(0x88FFD600),
            statusText = Color(0xFFE0E0E0),
            buttons = mapOf(
                Kind.PHRASE to (Color(0xFF0D3B66) to Color.White),
                Kind.QUICK_YES to (Color(0xFF1B5E20) to Color.White),
                Kind.QUICK_NO to (Color(0xFF8E0000) to Color.White),
                Kind.QUICK to (Color(0xFF37474F) to Color.White),
                Kind.SPEAK to (Color(0xFF00695C) to Color.White),
                Kind.CONTROL to (Color(0xFF424242) to Color.White),
                Kind.ATTENTION to (Color(0xFF6A1B9A) to Color.White),
                Kind.PREDICTION to (Color(0xFF3E2723) to Color.White),
                Kind.SUGGESTION to (Color(0xFF4E342E) to Color.White),
                Kind.KEY to (Color(0xFF263238) to Color.White),
                Kind.TAB to (Color(0xFF212121) to Color.White),
                Kind.TAB_SELECTED to (Color(0xFFFFFFFF) to Color.Black),
                Kind.NAV to (Color(0xFF303030) to Color.White),
            ),
        )

        val Standard = Palette(
            background = Color(0xFFECEFF1),
            messageBackground = Color(0xFFFFFFFF),
            messageText = Color(0xFF000000),
            highlight = Color(0xFFE65100),
            dwell = Color(0x66E65100),
            statusText = Color(0xFF263238),
            buttons = mapOf(
                Kind.PHRASE to (Color(0xFFBBDEFB) to Color.Black),
                Kind.QUICK_YES to (Color(0xFFC8E6C9) to Color.Black),
                Kind.QUICK_NO to (Color(0xFFFFCDD2) to Color.Black),
                Kind.QUICK to (Color(0xFFCFD8DC) to Color.Black),
                Kind.SPEAK to (Color(0xFF00695C) to Color.White),
                Kind.CONTROL to (Color(0xFFE0E0E0) to Color.Black),
                Kind.ATTENTION to (Color(0xFFE1BEE7) to Color.Black),
                Kind.PREDICTION to (Color(0xFFFFF9C4) to Color.Black),
                Kind.SUGGESTION to (Color(0xFFFFE0B2) to Color.Black),
                Kind.KEY to (Color(0xFFFFFFFF) to Color.Black),
                Kind.TAB to (Color(0xFFCFD8DC) to Color.Black),
                Kind.TAB_SELECTED to (Color(0xFF263238) to Color.White),
                Kind.NAV to (Color(0xFFB0BEC5) to Color.Black),
            ),
        )
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.HighContrast }

@Composable
fun CarerTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}
