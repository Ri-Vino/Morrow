package app.morrow.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.morrow.theme.Illustration
import app.morrow.weather.Feel
import app.morrow.weather.Warmth
import kotlin.math.PI
import kotlin.math.sin

/** What Pip wears and does when it [Feel]s like this outside. */
fun pipOutside(feel: Feel): PipOutside = PipOutside(
    scarf = feel.warmth == Warmth.FREEZING || feel.warmth == Warmth.COLD,
    beanie = feel.warmth == Warmth.FREEZING,
    shiverEvery = when (feel.warmth) {
        Warmth.FREEZING -> 3f
        Warmth.COLD -> 6f
        else -> 0f
    },
    breath = feel.warmth == Warmth.FREEZING || feel.warmth == Warmth.COLD,
    sweat = feel.warmth == Warmth.HOT || feel.warmth == Warmth.SCORCHING,
    fan = feel.warmth == Warmth.SCORCHING,
    windswept = feel.windy,
)

/** How far Pip shakes sideways at [seconds]: a quick shiver for the last half second of every [every] seconds. */
internal fun shiverAt(every: Float, seconds: Float): Float {
    if (every <= 0f) return 0f
    val left = every - seconds % every
    return if (left < SHIVER_SECONDS) 1.6f * sin(2f * PI.toFloat() * 12f * seconds) else 0f
}

private const val SHIVER_SECONDS = 0.5f

/** How far Pip leans right into the wind, which blows from the right, in degrees, swaying a little as it gusts. */
internal fun leanAt(seconds: Float): Float = 7f + 2f * sin(2f * PI.toFloat() * seconds / 2.6f)

/** A knitted hat over the top of Pip's head, with a bobble. */
internal fun DrawScope.drawBeanie() {
    drawPath(OutsidePaths.BeanieDome, Illustration.beanie)
    for (x in listOf(-14f, 0f, 14f)) {
        drawLine(Illustration.beanieTrim.copy(alpha = 0.5f), Offset(x, -32f), Offset(x * 1.15f, -19f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    drawRoundRect(Illustration.beanieTrim, Offset(-29f, -20f), Size(58f, 10f), CornerRadius(5f))
    drawCircle(Illustration.beanieTrim, 7f, Offset(0f, -40f))
}

/** A scarf round Pip's chin, its end hanging down, or blowing out to the left when [windswept]. */
internal fun DrawScope.drawScarf(windswept: Boolean, seconds: Float) {
    val swing = if (windswept) 70f + 8f * sin(2f * PI.toFloat() * seconds / 0.7f) else 4f * sin(2f * PI.toFloat() * seconds / 3f)
    rotate(swing, ScarfKnot) {
        drawPath(OutsidePaths.ScarfTail, Illustration.scarf)
        drawLine(Illustration.scarfStripe, Offset(15f, 37f), Offset(24f, 37f), strokeWidth = 2.2f)
        drawLine(Illustration.scarfStripe, Offset(16f, 43f), Offset(25f, 43f), strokeWidth = 2.2f)
    }
    drawPath(OutsidePaths.ScarfBand, Illustration.scarf)
    drawPath(OutsidePaths.ScarfBandStripe, Illustration.scarfStripe.copy(alpha = 0.8f), style = Stroke(2.2f, cap = StrokeCap.Round))
}

/** A drop of sweat that runs down Pip's brow every 2.4 s; it hangs at the top when [seconds] holds still. */
internal fun DrawScope.drawSweat(seconds: Float) {
    val p = seconds / 2.4f % 1f
    val alpha = if (p < 0.8f) 1f else (1f - p) / 0.2f
    translate(23f, -17f + 7f * p) { drawPath(OutsidePaths.SweatDrop, Illustration.sweat, alpha = alpha) }
}

/** A little paper fan at Pip's side, flapping back and forth three times a second. */
internal fun DrawScope.drawFan(seconds: Float) {
    translate(36f, 20f) {
        rotate(18f * sin(2f * PI.toFloat() * seconds * 3f), Offset.Zero) {
            drawLine(Illustration.sunInk, Offset.Zero, Offset(0f, 8f), strokeWidth = 2.4f, cap = StrokeCap.Round)
            drawArc(Illustration.fanPaper, -135f, 90f, useCenter = true, topLeft = Offset(-17f, -17f), size = Size(34f, 34f))
            for (angle in listOf(-120f, -90f, -60f)) {
                rotate(angle + 90f, Offset.Zero) { drawLine(Illustration.sun, Offset.Zero, Offset(0f, -16f), strokeWidth = 1.2f) }
            }
        }
    }
}

/**
 * A puff of breath from Pip's mouth every 3 s, swelling as it drifts off up and to the left and fading away, with a
 * smaller wisp leading it. Held mid-puff when [seconds] holds still at 0.
 */
internal fun DrawScope.drawBreath(seconds: Float) {
    val p = if (seconds == 0f) 0.3f else seconds / 3f % 1f
    if (p > 0.8f) return
    val spread = p / 0.8f
    val alpha = 0.75f * (if (spread < 0.15f) spread / 0.15f else 1f) * (1f - spread)
    translate(-34f - 22f * spread, 12f - 8f * spread) {
        scale(0.8f + 1f * spread, Offset.Zero) { drawPath(OutsidePaths.BreathPuff, Illustration.breath, alpha = alpha) }
        translate(-10f - 6f * spread, -5f) {
            scale(0.4f + 0.5f * spread, Offset.Zero) { drawPath(OutsidePaths.BreathPuff, Illustration.breath, alpha = alpha * 0.7f) }
        }
    }
}

/** The point the scarf's end hangs from, which it swings about. */
private val ScarfKnot = Offset(17f, 28f)

/**
 * Shapes in design units around Pip's centre; his face has a radius of 30. Built on first draw, so [pipOutside] never
 * touches the graphics path parser.
 */
private object OutsidePaths {
    val BeanieDome = svgPath("M-27 -13 C-27 -44 27 -44 27 -13 Z")
    val ScarfBand = svgPath("M-27 13 Q0 39 27 13 L28 21 Q0 46 -28 21 Z")
    val ScarfBandStripe = svgPath("M-22 21 Q0 38 22 21")
    val ScarfTail = svgPath("M12 27 L22 27 L27 50 L16 52 Z")
    val SweatDrop = svgPath("M0 -6 Q5 1 0 5 Q-5 1 0 -6 Z")
    val BreathPuff = svgPath("M-7 3 C-10 3 -10 -2 -6 -2 C-6 -6 -1 -7 1 -4 C2 -8 8 -7 7 -2 C10 -2 10 3 7 3 Z")
}
