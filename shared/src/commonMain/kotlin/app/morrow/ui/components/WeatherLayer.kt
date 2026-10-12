package app.morrow.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import app.morrow.theme.Illustration
import app.morrow.theme.isLightSky
import app.morrow.theme.mix
import app.morrow.weather.Sky

/**
 * [sky] as [weather] colours it, mixed toward grey, white or slate by the design's amounts, so the time of day still
 * shows through. [isDay] picks the daytime or night-time colour to mix toward.
 */
fun skyTint(sky: Color, weather: Sky, isDay: Boolean): Color {
    val amount = when (weather) {
        Sky.CLEAR -> return sky
        Sky.PARTLY_CLOUDY -> 0.12f
        Sky.CLOUDY -> 0.45f
        Sky.FOG -> 0.55f
        Sky.DRIZZLE -> 0.4f
        Sky.RAIN -> 0.55f
        Sky.STORM -> 0.72f
        Sky.SNOW -> 0.45f
    }
    val toward = when (weather) {
        Sky.FOG -> if (isDay) Illustration.tintFogDay else Illustration.tintFogNight
        Sky.SNOW -> if (isDay) Illustration.tintSnowDay else Illustration.tintSnowNight
        Sky.STORM -> if (isDay) Illustration.tintStormDay else Illustration.tintStormNight
        else -> if (isDay) Illustration.tintGreyDay else Illustration.tintGreyNight
    }
    return mix(sky, toward, amount)
}

/** What a [WeatherLayer] draws over one sky. */
data class WeatherLook(
    /** How opaque the clouds are. */
    val cloudAlpha: Float,
    val cloudColor: Color,
    /** Rain or drizzle falling, in [dropColor]. */
    val drops: Boolean,
    val dropColor: Color,
    val snow: Boolean,
    /** Bands of fog drifting across, in [fogColor]. */
    val fog: Boolean,
    val fogColor: Color,
    /** A lightning bolt that flashes every few seconds. */
    val lightning: Boolean,
)

/** The layer for [weather] over [sky], which [skyTint] has already coloured, or null for a clear sky, which has none. */
fun weatherLook(weather: Sky, sky: Color, isDay: Boolean): WeatherLook? {
    val cloudAlpha = when (weather) {
        Sky.CLEAR -> return null
        Sky.FOG -> 0.35f
        Sky.PARTLY_CLOUDY, Sky.SNOW -> 0.9f
        Sky.CLOUDY, Sky.DRIZZLE, Sky.RAIN, Sky.STORM -> 1f
    }
    return WeatherLook(
        cloudAlpha = cloudAlpha,
        cloudColor = when (weather) {
            Sky.STORM -> if (isDay) Illustration.stormCloudDay else Illustration.stormCloudNight
            Sky.PARTLY_CLOUDY -> if (isDay) Illustration.cloudBrightDay else Illustration.cloudBrightNight
            else -> if (isDay) Illustration.cloudDay else Illustration.cloudNight
        },
        drops = weather == Sky.RAIN || weather == Sky.DRIZZLE || weather == Sky.STORM,
        dropColor = if (sky.isLightSky()) Illustration.rainOnLight else Illustration.rainOnDark,
        snow = weather == Sky.SNOW,
        fog = weather == Sky.FOG,
        fogColor = if (isDay) Illustration.fogDay else Illustration.fogNight,
        lightning = weather == Sky.STORM,
    )
}

/**
 * Clouds drifting, rain or drizzle falling, snow, fog bands and a lightning flash, as [look] says, laid over a sky and
 * placed as [shapes] puts them. It moves on the scene clock, so it stops while the screen is off, and holds still when
 * "remove animations" is on. [pipY] is how far down Home's sky Pip is, for the cloud Pip peeks over. Decorative.
 */
@Composable
fun WeatherLayer(look: WeatherLook, shapes: WeatherShapes, modifier: Modifier = Modifier, pipY: () -> Float = { 0f }) {
    val seconds = rememberSceneSeconds(still = STILL)
    // Its own layer, so each frame redraws only the weather and not the scene and text around it.
    Canvas(modifier.graphicsLayer {}) {
        drawWeather(look, shapes, seconds.value, pipY())
    }
}

/** One cloud: the design's cloud path moved to [x], [y] and grown by [scale]. One [underPip] is placed [y] below Pip. */
internal class Cloud(val x: Float, val y: Float, val scale: Float, val underPip: Boolean = false)

/** Where a [WeatherLayer] draws everything, in the design units of [Home]'s 390 × 330 sky or a [Card]'s 342 × 128. */
class WeatherShapes private constructor(
    internal val width: Float,
    internal val height: Float,
    internal val clouds: List<Cloud>,
    /** The top of each rain streak; [streak] runs from there to its bottom. */
    internal val drops: List<Offset>,
    internal val dropDelays: List<Float>,
    internal val streak: Offset,
    internal val streakWidth: Float,
    internal val dropAlpha: Float,
    internal val flakes: List<Offset>,
    internal val flakeDelays: List<Float>,
    internal val flakeRadius: Float,
    internal val fogBands: List<Rect>,
    internal val fogAlpha: Float,
    /** Where the bolt's path, drawn from its own origin, starts. */
    internal val boltAt: Offset,
    private val boltData: String,
) {
    internal val bolt: Path by lazy { svgPath(boltData) }

    companion object {
        /** Over Home's sky, with the third cloud just under Pip. */
        val Home = WeatherShapes(
            width = 390f,
            height = 330f,
            clouds = listOf(Cloud(26f, 58f, 1.05f), Cloud(150f, 22f, 1.4f), Cloud(246f, 18f, 1.35f, underPip = true)),
            // The canvas's 30 streaks: 13 apart, in five staggered rows, each starting 0.17 s after the last.
            drops = List(30) { i -> Offset(8f + 13f * i, 70f + 37f * (i % 5) + 5f * (i / 5)) },
            dropDelays = List(30) { i -> (0.17f * i) % RAIN_SECONDS },
            streak = Offset(-4f, 13f),
            streakWidth = 2.2f,
            dropAlpha = 0.75f,
            // Its 26 flakes, 15 apart, each starting 0.41 s after the last.
            flakes = List(26) { i -> Offset(10f + 15f * i, 60f + (31 * i % 190)) },
            flakeDelays = List(26) { i -> (0.41f * i) % SNOW_SECONDS },
            flakeRadius = 2.4f,
            fogBands = listOf(Rect(-40f, 176f, 260f, 192f), Rect(150f, 204f, 470f, 218f), Rect(-20f, 232f, 240f, 244f)),
            fogAlpha = 0.55f,
            boltAt = Offset(128f, 70f),
            boltData = "M0 0 l-14 26 h12 l-9 22 l24 -32 h-12 l9 -16 z",
        )

        /** Over a city card's sky. */
        val Card = WeatherShapes(
            width = 342f,
            height = 128f,
            clouds = listOf(Cloud(150f, 6f, 0.9f), Cloud(238f, 26f, 0.7f), Cloud(44f, 18f, 0.65f)),
            // The canvas's 14 streaks, 23 apart, and 16 flakes, 21 apart.
            drops = List(14) { i -> Offset(20f + 23f * i, 30f + (37 * i % 50)) },
            dropDelays = List(14) { i -> (0.17f * i) % RAIN_SECONDS },
            streak = Offset(-3f, 9f),
            streakWidth = 1.8f,
            dropAlpha = 0.8f,
            flakes = List(16) { i -> Offset(14f + 21f * i, 18f + (31 * i % 70)) },
            flakeDelays = List(16) { i -> (0.41f * i) % SNOW_SECONDS },
            flakeRadius = 2f,
            fogBands = listOf(Rect(-20f, 56f, 200f, 66f), Rect(120f, 74f, 380f, 83f), Rect(10f, 90f, 210f, 98f)),
            fogAlpha = 0.5f,
            boltAt = Offset(226f, 46f),
            boltData = "M0 0 l-9 16 h8 l-6 14 l16 -20 h-8 l6 -10 z",
        )
    }
}

/**
 * Draws the weather at [seconds] on the scene clock, or still at [STILL]. Positions stretch with the space it's given,
 * as the hills do; the shapes themselves grow with its height only, as the sun and moon do, so they keep their shape.
 */
internal fun DrawScope.drawWeather(look: WeatherLook, shapes: WeatherShapes, seconds: Float, pipY: Float) {
    val across = size.width / shapes.width
    val down = size.height / shapes.height
    val still = seconds == STILL

    val cloudShift = if (still) 0f else swing(seconds, CLOUD_DRIFT_SECONDS, -6f, 8f)
    for (cloud in shapes.clouds) {
        val y = if (cloud.underPip) pipY + cloud.y else cloud.y
        withTransform({
            translate((cloud.x + cloudShift) * across, y * down)
            scale(cloud.scale * down, cloud.scale * down, Offset.Zero)
        }) {
            drawPath(WeatherPaths.Cloud, look.cloudColor, alpha = look.cloudAlpha)
        }
    }

    if (look.drops) {
        shapes.drops.forEachIndexed { i, top ->
            var shift = Offset.Zero
            var fade = 1f
            if (!still) {
                // Each streak falls 28 units at a slant in 0.9 s, fading in over the first 30% and out over the rest.
                val p = phase(seconds - shapes.dropDelays[i], RAIN_SECONDS)
                shift = Offset(4f - 8f * p, -14f + 28f * p)
                fade = if (p < 0.3f) 0.9f * p / 0.3f else 0.9f * (1f - p) / 0.7f
            }
            val start = Offset(top.x * across, top.y * down) + shift * down
            drawLine(
                look.dropColor,
                start,
                start + shapes.streak * down,
                strokeWidth = shapes.streakWidth * down,
                cap = StrokeCap.Round,
                alpha = shapes.dropAlpha * fade,
            )
        }
    }

    if (look.snow) {
        shapes.flakes.forEachIndexed { i, centre ->
            var shift = Offset.Zero
            var fade = 1f
            if (!still) {
                // Each flake drifts 36 down and 6 across in 3.4 s, fading in over the first 20% and out over the rest.
                val p = phase(seconds - shapes.flakeDelays[i], SNOW_SECONDS)
                shift = Offset(6f * p, -10f + 36f * p)
                fade = if (p < 0.2f) p / 0.2f else (1f - p) / 0.8f
            }
            drawCircle(Illustration.snowflake, shapes.flakeRadius * down, Offset(centre.x * across, centre.y * down) + shift * down, alpha = fade)
        }
    }

    if (look.fog) {
        val fogShift = if (still) 0f else swing(seconds, FOG_DRIFT_SECONDS, -10f, 10f)
        for (band in shapes.fogBands) {
            val corner = band.height / 2f * down
            drawRoundRect(
                look.fogColor,
                Offset((band.left + fogShift) * across, band.top * down),
                Size(band.width * across, band.height * down),
                CornerRadius(corner, corner),
                alpha = shapes.fogAlpha,
            )
        }
    }

    if (look.lightning) {
        val flash = if (still) 1f else flash(seconds)
        if (flash > 0f) {
            withTransform({
                translate(shapes.boltAt.x * across, shapes.boltAt.y * down)
                scale(down, down, Offset.Zero)
            }) {
                drawPath(shapes.bolt, Illustration.lightning, alpha = flash)
            }
        }
    }
}

/** How far through its [period] a loop is at [seconds], from 0 to 1. */
private fun phase(seconds: Float, period: Float): Float {
    val p = (seconds / period) % 1f
    return if (p < 0f) p + 1f else p
}

/** Back and forth between [from] and [to], easing in and out, [period] seconds each way. */
private fun swing(seconds: Float, period: Float, from: Float, to: Float): Float {
    val legs = seconds / period
    val leg = legs.toInt()
    val eased = EaseInOut.transform(legs - leg)
    return from + (to - from) * (if (leg % 2 == 0) eased else 1f - eased)
}

/** The bolt flashes twice every 5 s: fully at 46% of the way through, then dimmer at 52%, for a tenth of a second each. */
private fun flash(seconds: Float): Float {
    val p = phase(seconds, FLASH_SECONDS)
    return when {
        p >= 0.46f && p < 0.48f -> 1f
        p >= 0.52f && p < 0.54f -> 0.8f
        else -> 0f
    }
}

// The scene clock never counts below zero, so this stands for the still frame: everything at rest where the design draws it.
private const val STILL = -1f

private const val RAIN_SECONDS = 0.9f
private const val SNOW_SECONDS = 3.4f
private const val CLOUD_DRIFT_SECONDS = 14f
private const val FOG_DRIFT_SECONDS = 9f
private const val FLASH_SECONDS = 5f

/** Built on first draw, so the pure functions above never touch the graphics path parser. */
private object WeatherPaths {
    val Cloud = svgPath("M18 40 h46 a14 14 0 0 0 1.5 -27.9 a20 20 0 0 0 -37.5 -5.1 a14 14 0 0 0 -10 33 z")
}
