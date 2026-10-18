package app.morrow.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.morrow.theme.Holiday
import app.morrow.theme.Illustration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Holiday touches for Home's 390 × 330 scene, in its units. Each moves on the scene clock, so with "remove animations"
// on it holds still at the clock's 0 and the scene keeps its decorations.

/** Fireworks bursting in turn over the hills on New Year's night, as bright as the stars are. */
internal fun DrawScope.drawFireworks(starAlpha: Float, t: Float) {
    if (starAlpha <= 0f) return
    Bursts.forEachIndexed { i, burst ->
        // Each bursts for the first 70% of its 2.7 s, then the next takes over. At 0 the first is half open.
        val p = ((t + 1f + i * 0.9f) / 2.7f) % 1f
        if (p > 0.7f) return@forEachIndexed
        val open = p / 0.7f
        val reach = 6f + 22f * (1f - (1f - open) * (1f - open))
        val alpha = starAlpha * (1f - open * open)
        val color = Illustration.fairyLights[i % Illustration.fairyLights.size]
        repeat(12) { ray ->
            val angle = ray * PI.toFloat() / 6f
            val direction = Offset(cos(angle), sin(angle))
            drawLine(color, burst + direction * (reach * 0.55f), burst + direction * reach, strokeWidth = 2f, cap = StrokeCap.Round, alpha = alpha)
            drawCircle(color, 1.4f, burst + direction * (reach + 3f), alpha = alpha * 0.7f)
        }
    }
}

private val Bursts = listOf(Offset(96f, 84f), Offset(188f, 52f), Offset(150f, 140f))

/** Three bats flitting across the sky from left to right, each on its own wavy path, flapping as they go. */
internal fun DrawScope.drawBats(t: Float) {
    for ((i, bat) in Bats.withIndex()) {
        val p = ((t + bat.delay) / bat.crossing) % 1f
        val x = -30f + 450f * p
        val y = bat.y + 14f * sin(2f * PI.toFloat() * p * 3f + i)
        val flap = 0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * t / 0.28f + i))
        translate(x, y) {
            scale(bat.size, Offset.Zero) {
                scale(1f, flap, Offset.Zero) { drawPath(HolidayPaths.BatWings, Illustration.bat) }
                drawCircle(Illustration.bat, 3f, Offset.Zero)
                drawPath(HolidayPaths.BatEars, Illustration.bat)
            }
        }
    }
}

private class Bat(val y: Float, val crossing: Float, val delay: Float, val size: Float)

private val Bats = listOf(Bat(70f, 11f, 2f, 1.1f), Bat(128f, 14f, 9f, 0.85f), Bat(46f, 17f, 5f, 0.7f))

/** A carved pumpkin glowing on the hill by the cottage, its candle flickering. */
internal fun DrawScope.drawPumpkin(t: Float) {
    translate(28f, 252f) {
        val flicker = 0.75f + 0.25f * sin(2f * PI.toFloat() * t / 0.9f) * sin(2f * PI.toFloat() * t / 1.7f)
        drawCircle(
            Brush.radialGradient(listOf(Illustration.pumpkin.copy(alpha = 0.45f * flicker), Illustration.pumpkin.copy(alpha = 0f)), Offset.Zero, 24f),
            24f,
            Offset.Zero,
        )
        drawOval(Illustration.pumpkin, Offset(-11f, -8f), Size(22f, 16f))
        drawLine(Illustration.pumpkinStem, Offset(0f, -7f), Offset(2f, -12f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawPath(HolidayPaths.PumpkinFace, Illustration.pumpkinFace, alpha = flicker)
    }
}

/** A little fir with a star, to the right of the cottage. */
internal fun DrawScope.drawTree() {
    translate(122f, 258f) {
        drawPath(HolidayPaths.Tree, Illustration.tree)
        drawPath(HolidayPaths.Star, Illustration.treeStar)
    }
}

/** Fairy lights along the cottage's roof, twinkling in turn. */
internal fun DrawScope.drawRoofLights(t: Float) {
    RoofBulbs.forEachIndexed { i, at ->
        val twinkle = 0.55f + 0.45f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t / 1.6f + i * 0.37f)))
        drawCircle(Illustration.fairyLights[i % Illustration.fairyLights.size], 1.9f, at, alpha = twinkle)
    }
}

// Seven bulbs down each side of the roof, which runs from (44, 240) up to (72, 216) and down to (100, 240).
private val RoofBulbs = List(14) { i ->
    val f = (i % 7 + 0.5f) / 7f
    if (i < 7) Offset(44f + 28f * f, 241f - 24f * f) else Offset(100f - 28f * f, 241f - 24f * f)
}

/** Light snow over the whole scene for Christmas, gentler than a real snowfall, which takes its place when there's one. */
internal fun DrawScope.drawHolidaySnow(t: Float) {
    repeat(FLAKES) { i ->
        val p = ((t + i * 0.53f) / 6f) % 1f
        val x = (17f + 23f * i) % 390f + 8f * sin(2f * PI.toFloat() * (p * 2f + i * 0.2f))
        val y = -10f + 300f * p
        val alpha = if (p < 0.1f) p / 0.1f else if (p > 0.85f) (1f - p) / 0.15f else 1f
        drawCircle(Illustration.snowflake, 1.6f + (i % 3) * 0.4f, Offset(x, y), alpha = 0.85f * alpha)
    }
}

private const val FLAKES = 22

/** Built on first draw, so pure code never touches the graphics path parser. */
private object HolidayPaths {
    val BatWings = svgPath("M0 0 Q-5 -6 -13 -3 Q-10 -1 -11 2 Q-7 0 -5 2 Q-3 0 0 1 Q3 0 5 2 Q7 0 11 2 Q10 -1 13 -3 Q5 -6 0 0 Z")
    val BatEars = svgPath("M-2.2 -2 l-0.6 -3.2 l2 1.6 zM2.2 -2 l0.6 -3.2 l-2 1.6 z")
    val PumpkinFace = svgPath("M-6 -3 l2 -3 l2 3 zM2 -3 l2 -3 l2 3 zM-6 2 q6 4 12 0 l-2 1 l-2 -1 l-2 1 l-2 -1 l-2 1 z")
    val Tree = svgPath("M0 -40 L9 -26 L5 -26 L13 -13 L8 -13 L16 0 L-16 0 L-8 -13 L-13 -13 L-5 -26 L-9 -26 Z")
    val Star = svgPath("M0 -46 l1.8 3.8 l4 0.4 l-3 2.7 l0.9 4 l-3.7 -2.1 l-3.7 2.1 l0.9 -4 l-3 -2.7 l4 -0.4 z")
}

