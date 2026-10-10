package app.morrow.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class MorrowColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val hi: Color,
    /** Only for problems with an alarm: one that can't ring, or games that outlast its singing. */
    val warn: Color,
    val onWarn: Color,
    val knobOff: Color,
    val shadow: Color,
    val hillTint: Color,
    val houseTint: Color,
)

fun ThemeKind.colors(): MorrowColors = when (this) {
    ThemeKind.MORNING -> Morning
    ThemeKind.NOON -> Noon
    ThemeKind.AFTERNOON -> Afternoon
    ThemeKind.NIGHT -> Night
}

private val Morning = MorrowColors(
    bg = Color(0xFFEEF6FC),
    surface = Color(0xFFDCEDF9),
    surface2 = Color(0xFFC9E2F4),
    line = Color(0xFFAFD0EA),
    text = Color(0xFF13314B),
    muted = Color(0xFF3F5D77),
    accent = Color(0xFF5AA9E6),
    onAccent = Color(0xFF0D2338),
    hi = Color(0xFF1B6199),
    warn = Color(0xFFA82018),
    onWarn = Color(0xFFFFFFFF),
    knobOff = Color(0xFFFFFFFF),
    shadow = Color(30, 80, 130, alpha = 41),
    hillTint = Color(0xFF6FA58A),
    houseTint = Color(0xFF3E5A6E),
)

private val Noon = MorrowColors(
    bg = Color(0xFFFFFBF2),
    surface = Color(0xFFFFF1D6),
    surface2 = Color(0xFFFFE6B5),
    line = Color(0xFFEED7A8),
    text = Color(0xFF2E2214),
    muted = Color(0xFF6B5640),
    accent = Color(0xFFF2B33D),
    onAccent = Color(0xFF2E2214),
    hi = Color(0xFF8A5300),
    warn = Color(0xFFB42318),
    onWarn = Color(0xFFFFFFFF),
    knobOff = Color(0xFFFFFFFF),
    shadow = Color(120, 90, 30, alpha = 36),
    hillTint = Color(0xFFD9A75A),
    houseTint = Color(0xFF8C6233),
)

private val Afternoon = MorrowColors(
    bg = Color(0xFFF7E3CC),
    surface = Color(0xFFFBEEDF),
    surface2 = Color(0xFFF0D2B2),
    line = Color(0xFFE2B892),
    text = Color(0xFF3B1F14),
    muted = Color(0xFF74483A),
    accent = Color(0xFFB8522A),
    onAccent = Color(0xFFFFF6EC),
    hi = Color(0xFF94381A),
    warn = Color(0xFFA3241A),
    onWarn = Color(0xFFFFFFFF),
    knobOff = Color(0xFFFFFFFF),
    shadow = Color(120, 60, 30, alpha = 46),
    hillTint = Color(0xFFB4603C),
    houseTint = Color(0xFF7A3A24),
)

private val Night = MorrowColors(
    bg = Color(0xFF1F1411),
    surface = Color(0xFF2B1D18),
    surface2 = Color(0xFF3A2820),
    line = Color(0xFF4A3429),
    text = Color(0xFFFBEEDD),
    muted = Color(0xFFCDB5A0),
    accent = Color(0xFFF5A65B),
    onAccent = Color(0xFF2B1712),
    hi = Color(0xFFFFC97A),
    warn = Color(0xFFFF8A75),
    onWarn = Color(0xFF2B1712),
    knobOff = Color(0xFFE7D3BD),
    shadow = Color(10, 5, 3, alpha = 115),
    hillTint = Color(0xFF1F1411),
    houseTint = Color(0xFF1F1411),
)
