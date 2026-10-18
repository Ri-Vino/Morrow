package app.morrow.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import app.morrow.theme.Holiday
import app.morrow.theme.Illustration
import kotlin.math.PI
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
        holiday == Holiday.NEW_YEAR -> rotate(-14f, Offset(0f, -22f)) {
            drawPath(CostumePaths.PartyCone, Illustration.partyHat)
            drawPath(CostumePaths.PartyStripes, Illustration.partyStripe, style = Stroke(2.4f, cap = StrokeCap.Round))
            drawCircle(Illustration.partyPompom, 5f, Offset(0f, -62f))
        }
        else -> {
            drawPath(CostumePaths.SantaHat, Illustration.santaRed)
            drawRoundRect(Illustration.santaTrim, Offset(-29f, -20f), Size(58f, 9f), CornerRadius(4.5f))
            // The bobble swings gently on the drooping tip.
            val swing = 3f * sin(2f * PI.toFloat() * seconds / 2.8f)
            drawCircle(Illustration.santaTrim, 6.5f, Offset(42f + swing, -36f))
        }
    }
}

/** A party blower from Pip's mouth, unrolling and curling back up every 1.6 s; fully out when [seconds] holds still. */
internal fun DrawScope.drawNoisemaker(seconds: Float) {
    val out = if (seconds == 0f) 1f else 0.55f + 0.45f * (0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / 1.6f))
    scale(out, 1f, Offset(4f, 12f)) {
        drawLine(Illustration.partyPompom, Offset(4f, 12f), Offset(28f, 16f), strokeWidth = 4f, cap = StrokeCap.Round)
        for (x in listOf(10f, 17f, 24f)) {
            drawLine(Illustration.partyStripe, Offset(x, 11.2f + (x - 4f) / 6f), Offset(x, 15.8f + (x - 4f) / 6f), strokeWidth = 1.6f)
        }
    }
    drawCircle(Illustration.partyStripe, 2.6f, Offset(4f, 12f))
}

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
    val PartyStripes = svgPath("M-9 -33 L4 -31M-5 -44 L4 -42M-2 -54 L2 -53")
}
