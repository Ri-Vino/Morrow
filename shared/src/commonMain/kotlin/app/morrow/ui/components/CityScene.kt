package app.morrow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.isLightSky
import app.morrow.theme.mix
import app.morrow.theme.sunElevation
import app.morrow.weather.Sky
import kotlin.math.abs

/** What a city card's scene shows at a given minute of that city's day, in the design's 342 × 128 units. */
data class CitySceneState(
    /** The sky for the time of day, tinted by the weather. */
    val sky: Color,
    val starAlpha: Float,
    /** True for the sun, false for the moon in its nightcap. */
    val sunUp: Boolean,
    val bodyX: Float,
    val bodyY: Float,
    /** How much of the sun or moon shows: all of it under a clear or bright sky, half through fog, none behind heavier weather. */
    val bodyAlpha: Float,
    /** The sun wears shades from 11:00 to 15:00. */
    val shades: Boolean,
    val hillBack: Color,
    val hillFront: Color,
    /** What's drawn over the sky, or null for nothing. */
    val weather: WeatherLook?,
)

/** The scene at [minuteOfDay] under [weather], or under the time of day's own sky when there's none to show. */
fun cityScene(minuteOfDay: Int, weather: Sky? = null): CitySceneState {
    val minute = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val elevation = sunElevation(minute.toFloat()).toFloat()
    val sunUp = minute in SUNRISE until SUNSET
    val sky = SkyCurve.at(minute.toFloat()).let { if (weather != null) skyTint(it, weather, sunUp) else it }
    val bright = weather == null || weather == Sky.CLEAR || weather == Sky.PARTLY_CLOUDY
    val travelled = if (sunUp) minute - SUNRISE else (minute - SUNSET + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val snowy = weather == Sky.SNOW
    return CitySceneState(
        sky = sky,
        starAlpha = if (sunUp || !bright) 0f else (-elevation * 2.5f).coerceIn(0f, 1f),
        sunUp = sunUp,
        bodyX = BODY_LEFT + travelled / 720f * BODY_SPAN,
        bodyY = HORIZON - abs(elevation) * BODY_RISE,
        bodyAlpha = when {
            bright -> 1f
            weather == Sky.FOG -> 0.5f
            else -> 0f
        },
        shades = minute in 11 * 60 until 15 * 60,
        hillBack = if (snowy) mix(sky, Illustration.snowHillBack, 0.5f) else mix(sky, Illustration.ground, 0.3f),
        hillFront = if (snowy) mix(sky, Illustration.snowHillFront, 0.78f) else mix(sky, Illustration.ground, 0.5f),
        weather = weather?.let { weatherLook(it, sky.isLightSky(), sunUp) },
    )
}

/**
 * A small sky for a city card, as [state] has it: the sun in shades or the moon in a nightcap crossing the card as the
 * city's day goes by, the weather, and two hills in front. Pip holds still, because it sits in a list; only the weather
 * moves. It fills the space it is given; the card's text is laid over it.
 */
@Composable
fun CityScene(state: CitySceneState, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val across = size.width / SCENE_WIDTH
            val down = size.height / SCENE_HEIGHT
            drawRect(state.sky)
            if (state.starAlpha > 0f) {
                Stars.forEach { (x, y, r) -> drawCircle(Illustration.starlight.copy(alpha = state.starAlpha), r * down, Offset(x * across, y * down)) }
            }
            translate(state.bodyX * across, state.bodyY * down) {
                scale(down, Offset.Zero) {
                    withAlpha(state.bodyAlpha, BodyBounds) {
                        if (state.sunUp) drawSun(state.shades) else drawMoon()
                    }
                }
            }
        }
        state.weather?.let { WeatherLayer(it, WeatherShapes.Card, Modifier.matchParentSize()) }
        Canvas(Modifier.matchParentSize()) {
            scale(size.width / SCENE_WIDTH, size.height / SCENE_HEIGHT, Offset.Zero) {
                drawPath(CityPaths.HillBack, state.hillBack)
                drawPath(CityPaths.HillFront, state.hillFront)
            }
        }
    }
}

private fun DrawScope.drawSun(shades: Boolean) {
    val ink = Illustration.sunInk
    drawCircle(Illustration.sunLight.copy(alpha = 0.35f), 24f, Offset.Zero)
    drawCircle(Illustration.sun, 14f, Offset.Zero)
    drawCircle(Illustration.sunLight.copy(alpha = 0.75f), 4.5f, Offset(-4f, -5f))
    if (shades) {
        drawRoundRect(Illustration.shades, Offset(-10f, -5f), Size(8.5f, 5.5f), CornerRadius(2f))
        drawRoundRect(Illustration.shades, Offset(1.5f, -5f), Size(8.5f, 5.5f), CornerRadius(2f))
        drawLine(Illustration.shades, Offset(-1.5f, -3f), Offset(1.5f, -3f), strokeWidth = 1.4f)
    } else {
        drawCircle(ink, 1.7f, Offset(-4.5f, -1.5f))
        drawCircle(ink, 1.7f, Offset(4.5f, -1.5f))
    }
    drawCircle(Illustration.sunCheek.copy(alpha = 0.75f), 2.2f, Offset(-8.5f, 3.5f))
    drawCircle(Illustration.sunCheek.copy(alpha = 0.75f), 2.2f, Offset(8.5f, 3.5f))
    drawPath(CityPaths.SunSmile, ink, style = Stroke(1.4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawMoon() {
    drawCircle(Illustration.moonGlow.copy(alpha = 0.18f), 22f, Offset.Zero)
    drawCircle(Illustration.moon, 12f, Offset.Zero)
    drawCircle(Illustration.moonCrater, 2f, Offset(5f, 5f))
    drawPath(CityPaths.MoonEyes, Illustration.moonInk, style = Stroke(1.3f, cap = StrokeCap.Round))
    drawPath(CityPaths.Cap, Illustration.nightcap)
    drawRoundRect(Illustration.nightcapTrim, Offset(-11f, -7.5f), Size(23f, 4f), CornerRadius(2f))
    drawCircle(Illustration.nightcapTrim, 2.6f, Offset(17f, -14f))
}

private const val MINUTES_PER_DAY = 24 * 60
private const val SUNRISE = 6 * 60
private const val SUNSET = 18 * 60

// The design's card scene: 342 × 128, the sun and moon crossing x 40 to 302 and rising 76 above the horizon at y 104.
private const val SCENE_WIDTH = 342f
private const val SCENE_HEIGHT = 128f
private const val BODY_LEFT = 40f
private const val BODY_SPAN = 262f
private const val HORIZON = 104f
private const val BODY_RISE = 76f

/** Holds the sun's glow and the moon's nightcap, around the body's centre. */
private val BodyBounds = Rect(-26f, -26f, 26f, 26f)

private val Stars = listOf(
    Triple(28f, 22f, 1.3f), Triple(92f, 44f, 1.1f), Triple(150f, 16f, 1.4f), Triple(206f, 52f, 1.1f),
    Triple(262f, 20f, 1.3f), Triple(318f, 48f, 1.2f), Triple(122f, 72f, 1f), Triple(292f, 76f, 1f),
)

/** Built on first draw, so the pure scene logic above never touches the graphics path parser. */
private object CityPaths {
    val HillBack = svgPath("M0 104 C60 92 120 96 190 100 C250 104 300 94 342 98 L342 128 L0 128 Z")
    val HillFront = svgPath("M0 116 C90 108 200 120 342 110 L342 128 L0 128 Z")
    val SunSmile = svgPath("M-2.5 5 q2.5 2 5 0")
    val MoonEyes = svgPath("M-6 0 q2 2 4 0M2 0 q2 2 4 0")
    val Cap = svgPath("M-10 -5 C-9 -15 5 -20 16 -14 C12 -12 10 -8 10 -5 Z")
}
