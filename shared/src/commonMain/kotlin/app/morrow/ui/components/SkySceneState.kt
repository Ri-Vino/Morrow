package app.morrow.ui.components

import androidx.compose.ui.graphics.Color
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import app.morrow.theme.isLightSky
import app.morrow.theme.mix
import app.morrow.theme.sunElevation
import app.morrow.weather.Sky
import kotlin.math.abs
import kotlin.math.min

/** Everything about the Home sky scene that depends on the time, theme and weather, in the design's 390 × 330 units. */
data class SkySceneState(
    /** The sky for the time of day, tinted by the weather. */
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
    /** What's drawn over the sky and Pip, or null for nothing. Pip stays in view, peeking over a cloud. */
    val weather: WeatherLook? = null,
)

/** [dim] is the phone's dark mode: a day sky is toned down a little towards the dark ground, as photos are. */
fun skySceneState(minuteOfDay: Float, theme: ThemeKind, weather: Sky? = null, dim: Boolean = false): SkySceneState {
    val elevation = sunElevation(minuteOfDay).toFloat()
    val sunUp = elevation >= 0f
    val night = theme == ThemeKind.NIGHT
    val dimmed = dim && !night
    val tints = theme.colors(dimmed)
    val sky = SkyCurve.at(minuteOfDay)
        .let { if (weather != null) skyTint(it, weather, sunUp) else it }
        .let { if (dimmed) mix(it, tints.bg, DIM_SKY) else it }
    val look = weather?.let { weatherLook(it, sky, sunUp) }
    // Dark ink over a bright sky comes from the light palette, since a dim palette's text is light.
    val inkTints = theme.colors()
    val lightSky = sky.isLightSky()
    // Stars glimmer through thin cloud but hardly at all through a full cover.
    val overcast = (look?.cloudAlpha ?: 0f) > 0.95f
    return SkySceneState(
        sky = sky,
        starAlpha = (-elevation * 2.5f).coerceIn(0f, 1f) * if (overcast) 0.2f else 1f,
        pipY = 238f - min(1f, abs(elevation)) * 150f,
        pip = pipFor(theme, sunUp, brightSky = weather == null || weather == Sky.CLEAR || weather == Sky.PARTLY_CLOUDY),
        hillBack = mix(sky, tints.hillTint, 0.38f),
        hillMid = mix(sky, tints.hillTint, 0.64f),
        house = mix(sky, tints.houseTint, 0.8f),
        windowLit = if (night) 1f else 0.12f,
        doorLit = if (night) 0.35f else 0f,
        smokeAlpha = if (night || theme == ThemeKind.MORNING) 0.4f else 0f,
        ink = if (lightSky) inkTints.text else Illustration.starlight,
        inkMuted = if (lightSky) inkTints.muted else Illustration.starlightMuted,
        weather = look,
    )
}

private const val DIM_SKY = 0.2f

/** Pip at noon wears shades only under a [brightSky]; otherwise its eyes are open, as in the morning. */
private fun pipFor(theme: ThemeKind, sunUp: Boolean, brightSky: Boolean): PipLook = when {
    !sunUp -> PipLook(PipMood.ASLEEP, moon = true, nightcap = true)
    theme == ThemeKind.MORNING -> PipLook(PipMood.OPEN, rays = true)
    theme == ThemeKind.NOON -> PipLook(if (brightSky) PipMood.SHADES else PipMood.OPEN, rays = true)
    theme == ThemeKind.AFTERNOON -> PipLook(PipMood.HAPPY, rays = true)
    else -> PipLook(PipMood.ASLEEP)
}
