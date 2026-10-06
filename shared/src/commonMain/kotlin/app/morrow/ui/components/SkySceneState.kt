package app.morrow.ui.components

import androidx.compose.ui.graphics.Color
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import app.morrow.theme.isLightSky
import app.morrow.theme.mix
import app.morrow.theme.sunElevation
import kotlin.math.abs
import kotlin.math.min

/** Everything about the Home sky scene that depends on the time and theme, in the design's 390 × 330 units. */
data class SkySceneState(
    val sky: Color,
    val starAlpha: Float,
    /** Centre of Pip, from the top of the scene. */
    val pipY: Float,
    val pip: PipLook,
    val hillBack: Color,
    val hillMid: Color,
    val house: Color,
    val windowLit: Float,
    val doorLit: Float,
    val smokeAlpha: Float,
    /** Text colours for words drawn over the sky. */
    val ink: Color,
    val inkMuted: Color,
)

fun skySceneState(minuteOfDay: Float, theme: ThemeKind): SkySceneState {
    val sky = SkyCurve.at(minuteOfDay)
    val elevation = sunElevation(minuteOfDay).toFloat()
    val sunUp = elevation >= 0f
    val tints = theme.colors()
    val night = theme == ThemeKind.NIGHT
    val lightSky = sky.isLightSky()
    return SkySceneState(
        sky = sky,
        starAlpha = (-elevation * 2.5f).coerceIn(0f, 1f),
        pipY = 238f - min(1f, abs(elevation)) * 150f,
        pip = pipFor(theme, sunUp),
        hillBack = mix(sky, tints.hillTint, 0.38f),
        hillMid = mix(sky, tints.hillTint, 0.64f),
        house = mix(sky, tints.houseTint, 0.8f),
        windowLit = if (night) 1f else 0.12f,
        doorLit = if (night) 0.35f else 0f,
        smokeAlpha = if (night || theme == ThemeKind.MORNING) 0.4f else 0f,
        ink = if (lightSky) tints.text else Illustration.starlight,
        inkMuted = if (lightSky) tints.muted else Illustration.starlightMuted,
    )
}

private fun pipFor(theme: ThemeKind, sunUp: Boolean): PipLook = when {
    !sunUp -> PipLook(PipMood.ASLEEP, moon = true, nightcap = true)
    theme == ThemeKind.MORNING -> PipLook(PipMood.OPEN, rays = true)
    theme == ThemeKind.NOON -> PipLook(PipMood.SHADES, rays = true)
    theme == ThemeKind.AFTERNOON -> PipLook(PipMood.HAPPY, rays = true)
    else -> PipLook(PipMood.ASLEEP)
}
