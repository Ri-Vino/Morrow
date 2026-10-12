package app.morrow.ui.timers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.morrow.theme.Illustration
import app.morrow.ui.components.svgPath

/**
 * A timer card's sky as [sky] has it: stars at dusk, Pip the sun sinking towards the hills, and the hills in front.
 * Pip moves a little with each second the timer runs. It is decorative, so screen readers skip it.
 */
@Composable
fun TimerSkyStrip(sky: TimerSky, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / STRIP_WIDTH, Offset.Zero) {
            drawRect(sky.sky, size = Size(STRIP_WIDTH, STRIP_HEIGHT))
            if (sky.starAlpha > 0f) {
                Stars.forEach { (x, y, r) -> drawCircle(Illustration.starlight.copy(alpha = sky.starAlpha), r, Offset(x, y)) }
            }
            translate(sky.sunX, sky.sunY) { drawPip(sky.sleepy) }
            drawPath(StripPaths.HillBack, sky.hillBack)
            drawPath(StripPaths.HillFront, sky.hillFront)
        }
    }
}

private fun DrawScope.drawPip(sleepy: Boolean) {
    val ink = Illustration.sunInk
    drawCircle(Illustration.sunLight.copy(alpha = 0.35f), 20f, Offset.Zero)
    drawCircle(Illustration.sun, 12f, Offset.Zero)
    drawCircle(Illustration.sunLight.copy(alpha = 0.75f), 3.8f, Offset(-3.5f, -4f))
    if (sleepy) {
        drawPath(StripPaths.SleepyEyes, ink, style = Stroke(1.4f, cap = StrokeCap.Round))
    } else {
        drawCircle(ink, 1.5f, Offset(-4f, -1f))
        drawCircle(ink, 1.5f, Offset(4f, -1f))
    }
    drawPath(StripPaths.Smile, ink, style = Stroke(1.2f, cap = StrokeCap.Round))
}

/** The strip's design units: 358 wide and 72 high. */
internal const val STRIP_WIDTH = 358f
internal const val STRIP_HEIGHT = 72f

private val Stars = listOf(Triple(40f, 16f, 1.2f), Triple(120f, 10f, 1f), Triple(200f, 22f, 1.2f), Triple(300f, 12f, 1f))

/** Built on first draw, so the pure model never touches the graphics path parser. */
private object StripPaths {
    val HillBack = svgPath("M0 56 C70 46 140 50 210 54 C270 57 320 48 358 51 L358 72 L0 72 Z")
    val HillFront = svgPath("M0 64 C100 58 220 68 358 61 L358 72 L0 72 Z")
    val SleepyEyes = svgPath("M-6 -1 h3.5M2.5 -1 h3.5")
    val Smile = svgPath("M-2 4 q2 1.6 4 0")
}
