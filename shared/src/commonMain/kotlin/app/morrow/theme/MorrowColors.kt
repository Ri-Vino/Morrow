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

/**
 * The theme's colours. [dim] is for a phone in dark mode: the day themes keep their colour on dark surfaces, and
 * Night, already dark, stays the same.
 */
fun ThemeKind.colors(dim: Boolean = false): MorrowColors = when (this) {
    ThemeKind.MORNING -> if (dim) DimMorning else Morning
    ThemeKind.NOON -> if (dim) DimNoon else Noon
    ThemeKind.AFTERNOON -> if (dim) DimAfternoon else Afternoon
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

// Midnight blue lit by moonlight: cool surfaces, a periwinkle accent and silvery text, with no warm tones.
private val Night = MorrowColors(
    bg = Color(0xFF0E1424),
    surface = Color(0xFF161E33),
    surface2 = Color(0xFF1F2942),
    line = Color(0xFF2C3854),
    text = Color(0xFFEAF0FB),
    muted = Color(0xFFAEBAD3),
    accent = Color(0xFF8FA8F0),
    onAccent = Color(0xFF0D1426),
    hi = Color(0xFFB9C9FF),
    warn = Color(0xFFFF8A80),
    onWarn = Color(0xFF2A0F12),
    knobOff = Color(0xFFD5DDEE),
    shadow = Color(2, 4, 12, alpha = 115),
    hillTint = Color(0xFF0E1424),
    houseTint = Color(0xFF0E1424),
)

// The dim day themes: each keeps its accent on cool surfaces as dark as Night's. Morning stays slate blue, Noon
// charcoal and Afternoon dusk violet, so none turns brown and none passes for Night's midnight blue. The hills and
// cottage step down from the sky towards the dark ground.

private val DimMorning = MorrowColors(
    bg = Color(0xFF0F1A24),
    surface = Color(0xFF172633),
    surface2 = Color(0xFF203344),
    line = Color(0xFF2C4357),
    text = Color(0xFFE9F3FC),
    muted = Color(0xFFA8C0D5),
    accent = Color(0xFF5AA9E6),
    onAccent = Color(0xFF0D2338),
    hi = Color(0xFF8FCBF7),
    warn = Color(0xFFFF8A80),
    onWarn = Color(0xFF2A0F12),
    knobOff = Color(0xFFD3E2EE),
    shadow = Color(2, 8, 14, alpha = 115),
    hillTint = Color(0xFF3A5952),
    houseTint = Color(0xFF243745),
)

private val DimNoon = MorrowColors(
    bg = Color(0xFF12161C),
    surface = Color(0xFF1B2028),
    surface2 = Color(0xFF252B35),
    line = Color(0xFF333B47),
    text = Color(0xFFF3F5F8),
    muted = Color(0xFFB8C0CC),
    accent = Color(0xFFF2B33D),
    onAccent = Color(0xFF2E2214),
    hi = Color(0xFFFFD37C),
    warn = Color(0xFFFF8A80),
    onWarn = Color(0xFF2A0F12),
    knobOff = Color(0xFFDCE1E8),
    shadow = Color(2, 4, 8, alpha = 115),
    hillTint = Color(0xFF4A5A44),
    houseTint = Color(0xFF2E3640),
)

private val DimAfternoon = MorrowColors(
    bg = Color(0xFF181424),
    surface = Color(0xFF221C31),
    surface2 = Color(0xFF2D253F),
    line = Color(0xFF3C3251),
    text = Color(0xFFF6F0FB),
    muted = Color(0xFFC6B9D6),
    accent = Color(0xFFEE8556),
    onAccent = Color(0xFF2A120A),
    hi = Color(0xFFFFB894),
    warn = Color(0xFFFF8A80),
    onWarn = Color(0xFF2A0F12),
    knobOff = Color(0xFFE2D9EC),
    shadow = Color(6, 3, 10, alpha = 115),
    hillTint = Color(0xFF4A3550),
    houseTint = Color(0xFF2F2338),
)
