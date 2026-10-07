package app.morrow.theme

import androidx.compose.ui.graphics.Color

/** Colours of Pip and the scene. Like the design, they stay the same in every theme. */
object Illustration {
    val sun = Color(0xFFF5A65B)
    val sunLight = Color(0xFFFFC97A)
    val sunInk = Color(0xFF3A2218)
    val sunCheek = Color(0xFFE8806A)
    val shades = Color(0xFF2B1712)
    val glint = Color(0xFFFFF4E6)

    val moon = Color(0xFFF3E6C8)
    val moonCrater = Color(0xFFE3D1AA)
    val moonLight = Color(0xFFFFF9EC)
    val moonGlow = Color(0xFFFFF1D0)
    val moonInk = Color(0xFF2E2238)
    val moonCheek = Color(0xFFE8A3A0)

    val nightcap = Color(0xFF6E5A9A)
    val nightcapTrim = Color(0xFFC9B8E0)

    /** Stars, smoke, z's, and text drawn on a dark sky. */
    val starlight = Color(0xFFFBEEDD)
    val starlightMuted = Color(0xFFE7CDB6)
    val windowGlow = Color(0xFFF5A65B)

    /** The pre-dawn sky that warms as Pip gets up, in the setup and mission screens. */
    val dawnNight = Color(0xFF241826)
    val dawnRose = Color(0xFF9A4757)
    val dawnGold = Color(0xFFF2B07A)
    val ground = Color(0xFF1F1411)

    /** Small labels laid over a sky. */
    val badge = Color(0x8C150E0C)
    val onBadge = Color(0xFFFFE3C2)

    /** The ringing screen's sky, outermost band first, each warming from its night colour to its dawn one. */
    val sunriseBands = listOf(
        Color(0xFF1C1322) to Color(0xFF552A44),
        Color(0xFF24182A) to Color(0xFF8E4356),
        Color(0xFF2E1D33) to Color(0xFFC9605C),
        Color(0xFF3A2236) to Color(0xFFEE9362),
    )
    val sunriseHill = Color(0xFF24171F) to Color(0xFF5A2E3A)
    val sunriseGreeting = Color(0xFFF3DCC6)
    val birds = Color(0xFF2B1712)
}
