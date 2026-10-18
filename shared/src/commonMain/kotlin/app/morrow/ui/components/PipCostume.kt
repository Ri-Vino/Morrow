package app.morrow.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.morrow.theme.Holiday
import app.morrow.theme.Illustration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pip's face paint for [holiday], drawn over his face: cat whiskers, a nose and purple eyeshadow for Halloween, with a
 * stitched scar and little fangs on the night itself; rosy cheeks for Christmas and New Year.
 */
internal fun DrawScope.drawFacePaint(holiday: Holiday) {
    if (holiday.halloween) {
        val paint = Illustration.facePaint
        drawPath(CostumePaths.EyeShadow, Illustration.eyeShadow.copy(alpha = 0.85f), style = Stroke(2.2f, cap = StrokeCap.Round))
        drawPath(CostumePaths.CatNose, paint)
        drawPath(CostumePaths.Whiskers, paint, style = Stroke(1.3f, cap = StrokeCap.Round))
        if (holiday == Holiday.HALLOWEEN_NIGHT) {
            drawPath(CostumePaths.Scar, paint, style = Stroke(1.4f, cap = StrokeCap.Round))
            drawPath(CostumePaths.Fangs, Illustration.glint)
        }
    } else {
        drawCircle(Illustration.rosyCheek.copy(alpha = 0.5f), 5.5f, Offset(-18f, 7f))
        drawCircle(Illustration.rosyCheek.copy(alpha = 0.5f), 5.5f, Offset(18f, 7f))
    }
}

/** The hat for [holiday], in place of a nightcap or beanie: a witch's hat, a Santa hat or a party hat. */
internal fun DrawScope.drawHolidayHat(holiday: Holiday, seconds: Float) {
    when {
        holiday.halloween -> {
            drawPath(CostumePaths.WitchCone, Illustration.witchHat)
            drawRect(Illustration.witchBand, Offset(-17f, -30f), Size(34f, 6f))
            drawOval(Illustration.witchHat, Offset(-37f, -26f), Size(74f, 9f))
        }
        holiday == Holiday.NEW_YEAR -> rotate(-14f, Offset(0f, -22f)) { drawPartyHat(seconds) }
        else -> {
            drawPath(CostumePaths.SantaHat, Illustration.santaRed)
            drawRoundRect(Illustration.santaTrim, Offset(-29f, -20f), Size(58f, 9f), CornerRadius(4.5f))
            // The bobble swings gently on the drooping tip.
            val swing = 3f * sin(2f * PI.toFloat() * seconds / 2.8f)
            drawCircle(Illustration.santaTrim, 6.5f, Offset(42f + swing, -36f))
        }
    }
}

/**
 * A polka-dot party hat with a tinsel brim, a shine down one side, and a pompom of streamers on its tip that sways.
 * Drawn upright; the caller tilts it.
 */
private fun DrawScope.drawPartyHat(seconds: Float) {
    drawPath(CostumePaths.PartyCone, Illustration.partyHat)
    drawPath(CostumePaths.PartyShine, Illustration.glint, alpha = 0.35f)
    PartyDots.forEachIndexed { i, dot ->
        drawCircle(if (i % 2 == 0) Illustration.partyStripe else Illustration.partyPompom, dot.radius, Offset(dot.x, dot.y))
    }
    drawRoundRect(Illustration.partyStripe, Offset(-15f, -25.5f), Size(30f, 5f), CornerRadius(2.5f))
    repeat(7) { i -> drawCircle(Illustration.glint, 0.9f, Offset(-12f + 4f * i, -23f), alpha = 0.8f) }
    rotate(8f * sin(2f * PI.toFloat() * seconds / 2.2f), PartyTip) {
        PartyStreamers.forEachIndexed { i, end ->
            drawLine(if (i % 2 == 0) Illustration.partyPompom else Illustration.partyStripe, PartyTip, end, strokeWidth = 2f, cap = StrokeCap.Round)
        }
        drawCircle(Illustration.partyPompom, 2.8f, PartyTip)
    }
}

/** Dots on the party hat. */
private val PartyDots = listOf(
    Dot(-4f, -30f, 2.2f), Dot(5f, -36f, 2f), Dot(-2f, -44f, 1.8f), Dot(1f, -52f, 1.3f), Dot(6f, -28f, 1.5f),
)

private class Dot(val x: Float, val y: Float, val radius: Float)

private val PartyTip = Offset(0f, -62f)

// A fountain of streamers fanning up from the tip, long and short in turn.
private val PartyStreamers = List(9) { k ->
    val angle = -PI.toFloat() / 2f + (k - 4) * 0.36f
    PartyTip + Offset(cos(angle), sin(angle)) * (6.5f + (k % 2) * 2f)
}

/**
 * A party horn from Pip's mouth that unrolls from a coil and curls back up every 1.6 s, its fringed end shaking out when
 * it's all the way out, as it is when [seconds] holds still.
 */
internal fun DrawScope.drawNoisemaker(seconds: Float) {
    val out = if (seconds == 0f) 1f else 0.5f + 0.5f * (0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / 1.6f))
    val length = 6f + 20f * out
    val end = HornMouth + HornAlong * length
    drawLine(Illustration.partyPompom, HornMouth, end, strokeWidth = 4f, cap = StrokeCap.Round)
    for (f in HornStripes) {
        val at = HornMouth + HornAlong * (length * f)
        drawLine(Illustration.partyStripe, at - HornAcross * 2f, at + HornAcross * 2f, strokeWidth = 1.6f)
    }
    val coil = 5f * (1f - out)
    if (coil > 0.6f) drawCircle(Illustration.partyPompom, coil, end + HornAcross * coil, style = HornCoil)
    val fringe = ((out - 0.75f) / 0.25f).coerceIn(0f, 1f)
    if (fringe > 0f) {
        for (spread in HornFringe) {
            val tip = end + (HornAlong + HornAcross * spread) * (4.5f * fringe)
            drawLine(Illustration.partyStripe, end, tip, strokeWidth = 1.4f, cap = StrokeCap.Round, alpha = fringe)
        }
    }
    drawCircle(Illustration.partyStripe, 2.6f, HornMouth)
}

private val HornMouth = Offset(4f, 12f)
/** Along the horn, out to Pip's right and a little down, and across it, downward. */
private val HornAlong = Offset(0.986f, 0.164f)
private val HornAcross = Offset(-0.164f, 0.986f)
private val HornCoil = Stroke(3.2f)
private val HornStripes = floatArrayOf(0.35f, 0.6f, 0.85f)
private val HornFringe = floatArrayOf(-0.5f, 0f, 0.5f)

/** Shapes in design units around Pip's centre; his face has a radius of 30. Built on first draw. */
private object CostumePaths {
    val EyeShadow = svgPath("M-14.5 -7.5 q5.5 -5 11 0M3.5 -7.5 q5.5 -5 11 0")
    val CatNose = svgPath("M-2.6 2.6 h5.2 l-2.6 3.2 z")
    val Whiskers = svgPath("M-12 7 l-13 -3M-12 9 l-14 1M-12 11 l-12 4M12 7 l13 -3M12 9 l14 1M12 11 l12 4")
    val Scar = svgPath("M8 -20 l13 11M10 -13 l5 -4M13 -11 l5 -4M16 -8 l5 -4")
    val Fangs = svgPath("M-4 11.5 l1.4 3 l1.4 -2.7 zM1.2 11.8 l1.4 2.7 l1.4 -3 z")
    val WitchCone = svgPath("M-18 -24 L2 -62 Q6 -72 14 -66 Q8 -64 7 -58 L18 -24 Z")
    val SantaHat = svgPath("M-26 -14 C-22 -42 12 -54 40 -36 C30 -32 26 -24 26 -14 Z")
    val PartyCone = svgPath("M-13 -22 L0 -62 L13 -22 Z")
    val PartyShine = svgPath("M-9.5 -27 L-2 -52 L-0.8 -50 L-7 -27 Z")
}
