package app.morrow.ui.components

import androidx.compose.ui.graphics.Color
import app.morrow.theme.Holiday
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import app.morrow.theme.contrastRatio
import app.morrow.theme.mix
import app.morrow.theme.sunElevation
import app.morrow.weather.Feel
import app.morrow.weather.Sky
import app.morrow.weather.Warmth
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
    /** Dark ink reads better over this sky than starlight. The status-bar icons and rain follow it too. */
    val lightSky: Boolean,
    /** Text colours for words drawn over the sky, each at least [TEXT_CONTRAST] against the sky through [backing]. */
    val ink: Color,
    val inkMuted: Color,
    /**
     * A wash laid under those words. Clear for most of the day: it fades in only around sunrise and sunset, when the sky
     * is too mid-toned for either ink to read on its own.
     */
    val backing: Color,
    /** What's drawn over the sky and Pip, or null for nothing. Pip stays in view, peeking over a cloud. */
    val weather: WeatherLook? = null,
    /** How warm or cold it feels, and how windy, for [ClimateLayer]. */
    val climate: ClimateLook = ClimateLook.None,
    /** The season the scene is dressed for, or null for an ordinary day. */
    val holiday: Holiday? = null,
)

/**
 * [dim] is the phone's dark mode: a day sky is toned down a little towards the dark ground, as photos are. [feel] is how
 * it feels outside, for frost or heat haze, blowing leaves, and what Pip wears. A [holiday] dresses Pip and the scene up,
 * and at Halloween turns the sky a little purple and lights the cottage.
 */
fun skySceneState(
    minuteOfDay: Float,
    theme: ThemeKind,
    weather: Sky? = null,
    dim: Boolean = false,
    feel: Feel = Feel.Mild,
    holiday: Holiday? = null,
): SkySceneState {
    val elevation = sunElevation(minuteOfDay).toFloat()
    val sunUp = elevation >= 0f
    val night = theme == ThemeKind.NIGHT
    val dimmed = dim && !night
    val tints = theme.colors(dimmed)
    val sky = SkyCurve.at(minuteOfDay)
        .let { if (weather != null) skyTint(it, weather, sunUp) else it }
        .let { if (holiday?.halloween == true) mix(it, Illustration.halloweenSky, if (sunUp) HALLOWEEN_DAY else HALLOWEEN_NIGHT) else it }
        .let { if (dimmed) mix(it, tints.bg, DIM_SKY) else it }
    // Dark ink over a bright sky comes from the light palette, since a dim palette's text is light. Night's text is
    // starlight itself, so a night sky never counts as light.
    val inkTints = theme.colors()
    val lightSky = contrastRatio(sky, inkTints.text) > contrastRatio(sky, Illustration.starlight)
    val ink = if (lightSky) inkTints.text else Illustration.starlight
    val wash = if (lightSky) inkTints.bg else theme.colors(dim = true).bg
    val washAlpha = leastStep { contrastRatio(ink, mix(sky, wash, it)) >= TEXT_CONTRAST }
    val behindText = if (washAlpha == 0f) sky else mix(sky, wash, washAlpha)
    // The muted ink deepens towards the full ink only as far as it must to stay readable.
    val muted = if (lightSky) inkTints.muted else Illustration.starlightMuted
    val deepen = leastStep { contrastRatio(mix(muted, ink, it), behindText) >= TEXT_CONTRAST }
    val look = weather?.let { weatherLook(it, lightSky, sunUp) }?.copy(windy = feel.windy)
    // Stars glimmer through thin cloud but hardly at all through a full cover.
    val overcast = (look?.cloudAlpha ?: 0f) > 0.95f
    return SkySceneState(
        sky = sky,
        starAlpha = (-elevation * 2.5f).coerceIn(0f, 1f) * if (overcast) 0.2f else 1f,
        pipY = 238f - min(1f, abs(elevation)) * 150f,
        pip = pipFor(theme, sunUp, brightSky = weather == null || weather == Sky.CLEAR || weather == Sky.PARTLY_CLOUDY, feel, holiday),
        hillBack = mix(sky, tints.hillTint, 0.38f),
        hillMid = mix(sky, tints.hillTint, 0.64f),
        house = mix(sky, tints.houseTint, 0.8f),
        windowLit = if (night || holiday?.halloween == true) 1f else 0.12f,
        doorLit = if (night) 0.35f else 0f,
        smokeAlpha = if (night || theme == ThemeKind.MORNING) 0.4f else 0f,
        lightSky = lightSky,
        ink = ink,
        inkMuted = if (deepen == 0f) muted else mix(muted, ink, deepen),
        backing = wash.copy(alpha = washAlpha),
        weather = look,
        climate = climateLook(feel, snowing = weather == Sky.SNOW),
        holiday = holiday,
    )
}

/** The contrast every word over the sky keeps: the date and weather line are small text. */
const val TEXT_CONTRAST = 4.5f

private const val DIM_SKY = 0.2f

/** How far a Halloween sky leans purple by day, and by night. */
private const val HALLOWEEN_DAY = 0.16f
private const val HALLOWEEN_NIGHT = 0.3f

/** The least of 0, 1/255 … 1 for which [ok] holds; steps of 1/255 so an alpha survives being stored in a colour. */
private inline fun leastStep(ok: (Float) -> Boolean): Float =
    (0..STEPS).firstOrNull { ok(it / STEPS.toFloat()) }?.let { it / STEPS.toFloat() } ?: 1f

private const val STEPS = 255

/**
 * Pip at noon wears shades only under a [brightSky]; otherwise its eyes are open, as in the morning. On a hot day he
 * wears them whenever the sun is up, and [feel] dresses him for the weather.
 */
private fun pipFor(theme: ThemeKind, sunUp: Boolean, brightSky: Boolean, feel: Feel, holiday: Holiday?): PipLook {
    val hot = feel.warmth == Warmth.HOT || feel.warmth == Warmth.SCORCHING
    val look = when {
        !sunUp -> PipLook(PipMood.ASLEEP, moon = true, nightcap = true)
        hot -> PipLook(PipMood.SHADES, rays = true)
        theme == ThemeKind.MORNING -> PipLook(PipMood.OPEN, rays = true)
        theme == ThemeKind.NOON -> PipLook(if (brightSky) PipMood.SHADES else PipMood.OPEN, rays = true)
        theme == ThemeKind.AFTERNOON -> PipLook(PipMood.HAPPY, rays = true)
        else -> PipLook(PipMood.ASLEEP)
    }
    return look.copy(outside = pipOutside(feel), holiday = holiday)
}
