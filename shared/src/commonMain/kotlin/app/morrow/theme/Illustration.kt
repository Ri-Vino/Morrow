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
    val starlight = Color(0xFFEEF2FB)
    val starlightMuted = Color(0xFFC3CCDF)
    val windowGlow = Color(0xFFF5A65B)

    /** The pre-dawn sky that warms as Pip gets up, in the setup and mission screens. */
    val dawnNight = Color(0xFF241826)
    val dawnRose = Color(0xFF9A4757)
    val dawnGold = Color(0xFFF2B07A)
    val ground = Color(0xFF0E1222)

    /** Small labels laid over a sky. */
    val badge = Color(0x8C0B0F1C)
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

    /** The Timer done screen's evening sky, behind its text. */
    val duskSky = Color(0xFF2E1D33)
    /** The rings of evening light around Pip on the Timer done screen, outermost first, and the hill below him. */
    val duskBands = listOf(Color(0xFF4A2540), Color(0xFF8E4356), Color(0xFFC9605C), Color(0xFFEE9362))
    val duskHill = Color(0xFF5A2E3A)

    /** Wrong answers and wrong codes in a mission. */
    val mistake = Color(0xFFE8806A)
    /** Behind the camera picture. */
    val viewfinder = Color(0xFF0B0E16)
    /** Dims the screen behind a bottom sheet. */
    val scrim = Color(0x730A0D18)

    /** Text on a city card whose sky is light, and on one whose sky is dark. */
    val cityInkDark = Color(0xFF16202B)
    val cityInkLight = Color(0xFFFFF6EA)
    /** The pale wash under dark card text, and the dark one under light card text. */
    val cityScrimLight = Color(0xFFFFFFFF)
    val cityScrimDark = Color(0xFF0B1020)

    /** What weather mixes a sky toward, by day and by night: grey, or its own colour in fog, snow and storms. */
    val tintGreyDay = Color(0xFFAEB9C4)
    val tintGreyNight = Color(0xFF2C2934)
    val tintFogDay = Color(0xFFD9DEE3)
    val tintFogNight = Color(0xFF3D3946)
    val tintSnowDay = Color(0xFFE4EAF0)
    val tintSnowNight = Color(0xFF3E4252)
    val tintStormDay = Color(0xFF6B7180)
    val tintStormNight = Color(0xFF24222C)

    /** Clouds by day and by night: bright in a partly cloudy sky, grey in heavier weather, dark in a storm. */
    val cloudBrightDay = Color(0xFFFFFFFF)
    val cloudBrightNight = Color(0xFF5A5468)
    val cloudDay = Color(0xFFE8EDF2)
    val cloudNight = Color(0xFF4A4557)
    val stormCloudDay = Color(0xFF7D8493)
    val stormCloudNight = Color(0xFF3A3744)

    /** Rain and drizzle over a light sky, and over a dark one. */
    val rainOnLight = Color(0xFF4F6B84)
    val rainOnDark = Color(0xFFC9D6E2)
    val snowflake = Color(0xFFFFFFFF)
    val fogDay = Color(0xFFFFFFFF)
    val fogNight = Color(0xFFB9B3C3)
    val lightning = Color(0xFFFFE08A)

    /** What Pip wears in the cold, and how he shows the heat. */
    val scarf = Color(0xFFD9534F)
    val scarfStripe = Color(0xFFFFF4E6)
    val beanie = Color(0xFF3F7CAC)
    val beanieTrim = Color(0xFFB9D7EE)
    val sweat = Color(0xFF8FD3F4)
    val fanPaper = Color(0xFFFFF4E6)
    val breath = Color(0xFFFFFFFF)

    /** Home's frost on a cold day, haze on a hot one, and the leaves a strong wind blows past. */
    val frost = Color(0xFFF4FAFF)
    val heatGlow = Color(0xFFFFB36B)
    val heatHaze = Color(0xFFFFE2B8)
    val leafWarm = Color(0xFFD98A3D)
    val leafGreen = Color(0xFF8DB36B)

    /** What a city card's back and front hills are mixed toward under snow. */
    val snowHillBack = Color(0xFFFFFFFF)
    val snowHillFront = Color(0xFFF4F7FA)
}
