package app.morrow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.weather.Sky
import kotlin.math.PI
import kotlin.math.cos

private const val SCENE_WIDTH = 390f
private const val SCENE_HEIGHT = 330f

/**
 * The Home header: the sky for [minuteOfDay], stars, Pip as the sun or the sleeping moon, the [weather] over them, then
 * hills and the cottage. Its bottom edge is drawn in the theme's background colour so the screen continues below it.
 * Decorative.
 */
@Composable
fun SkyScene(minuteOfDay: Float, theme: ThemeKind, modifier: Modifier = Modifier, weather: Sky? = null) {
    val target = skySceneState(minuteOfDay, theme, weather, MorrowTheme.dim)
    val slow = tween<Color>(durationMillis = 1200)
    val sky by animateColorAsState(target.sky, slow)
    val hillBack by animateColorAsState(target.hillBack, slow)
    val hillMid by animateColorAsState(target.hillMid, slow)
    val house by animateColorAsState(target.house, slow)
    val pipY by animateFloatAsState(target.pipY, tween(1200))
    val windowLit by animateFloatAsState(target.windowLit, tween(1000))
    val ground = MorrowTheme.colors.bg
    val seconds = rememberSceneSeconds()

    val zs = rememberZs(Illustration.starlight, sizes = listOf(16f, 12f, 10f))

    // The weather goes between Pip and the hills, so it's drawn on its own between the scene's back and front.
    Box(modifier.fillMaxWidth().aspectRatio(SCENE_WIDTH / SCENE_HEIGHT).clipToBounds()) {
        Canvas(Modifier.matchParentSize()) {
            val t = seconds.value
            scale(size.width / SCENE_WIDTH, Offset.Zero) {
                drawRect(sky, size = Size(SCENE_WIDTH, SCENE_HEIGHT))
                drawStars(target.starAlpha, t)
                translate(292f, pipY) {
                    drawPip(target.pip, t)
                    if (target.pip.mood == PipMood.ASLEEP) drawZs(zs, t, MoonZs)
                }
            }
        }
        target.weather?.let { WeatherLayer(it, WeatherShapes.Home, Modifier.matchParentSize(), pipY = { pipY }) }
        Canvas(Modifier.matchParentSize()) {
            val t = seconds.value
            scale(size.width / SCENE_WIDTH, Offset.Zero) {
                drawPath(HillBack, hillBack)
                drawCottage(house, windowLit, target.doorLit, target.smokeAlpha, t)
                drawPath(HillMid, hillMid)
                drawPath(HillFront, ground)
            }
        }
    }
}

private class Star(val x: Float, val y: Float, val radius: Float, val delay: Float)

private val Stars = listOf(
    Star(28f, 150f, 1.4f, 0f), Star(132f, 44f, 1.6f, 0.7f), Star(176f, 128f, 1.2f, 1.5f),
    Star(214f, 60f, 1.8f, 2.2f), Star(252f, 148f, 1.2f, 0.4f), Star(300f, 40f, 1.4f, 1.1f),
    Star(350f, 96f, 1.6f, 2.6f), Star(372f, 170f, 1.2f, 1.9f), Star(236f, 196f, 1.1f, 0.9f),
    Star(160f, 186f, 1.3f, 3f), Star(330f, 140f, 1.1f, 1.3f), Star(96f, 196f, 1.2f, 2.9f),
)

private fun DrawScope.drawStars(alpha: Float, t: Float) {
    if (alpha <= 0f) return
    for (star in Stars) {
        // Twinkles between 25% and 100% every 3.2 s.
        val twinkle = 0.25f + 0.75f * (0.5f - 0.5f * cos(2f * PI.toFloat() * (t - star.delay) / 3.2f))
        drawCircle(Illustration.starlight.copy(alpha = alpha * twinkle), star.radius, Offset(star.x, star.y))
    }
}


private fun DrawScope.drawCottage(
    house: Color,
    windowLit: Float,
    doorLit: Float,
    smokeAlpha: Float,
    t: Float,
) {
    if (smokeAlpha > 0f) {
        // A 5-on, 9-off dash that drifts up the chimney smoke once every 2.4 s.
        val phase = 14f - (28f * (t / 2.4f % 1f)) % 14f
        drawPath(
            Smoke,
            Illustration.starlight.copy(alpha = smokeAlpha),
            style = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 9f), phase)),
        )
    }
    drawRect(house, Offset(82f, 220f), Size(8f, 16f))
    drawPath(Roof, house)
    drawRect(house, Offset(50f, 238f), Size(44f, 30f))
    drawRoundRect(Illustration.windowGlow, Offset(57f, 245f), Size(13f, 11f), CornerRadius(2f), alpha = windowLit * flicker(t))
    if (doorLit > 0f) {
        drawRoundRect(Illustration.windowGlow, Offset(76f, 248f), Size(11f, 20f), CornerRadius(2f), alpha = doorLit)
    }
}

/** The window's candle dips twice every 3.5 s. */
private fun flicker(t: Float): Float = when ((t / 3.5f % 1f) * 100f) {
    in 31f..33f -> 0.82f
    in 71f..73f -> 0.9f
    else -> 1f
}

/** Where the z's start, to the moon's upper left. */
private val MoonZs = listOf(Offset(-52f, -34f), Offset(-62f, -48f), Offset(-44f, -56f))

private val HillBack = svgPath("M0 252 C70 224 140 228 210 242 C270 254 330 230 390 238 L390 330 L0 330 Z")
private val HillMid = svgPath("M0 272 C80 254 160 264 240 270 C300 275 350 262 390 266 L390 330 L0 330 Z")
// Runs a little past the bottom edge so no sky shows through the anti-aliased last row of pixels.
private val HillFront = svgPath("M0 304 C100 290 220 302 390 292 L390 334 L0 334 Z")
private val Roof = svgPath("M44 240 L72 216 L100 240 Z")
private val Smoke = svgPath("M86 216 c-6 -8 6 -14 0 -22 c-6 -8 6 -14 0 -22 c-6 -8 6 -14 0 -22")
