package app.morrow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import app.morrow.theme.Illustration
import kotlin.math.PI
import kotlin.math.cos

/** Pip on its own, scaled to fit. Pip is decorative, so it adds nothing for screen readers. */
@Composable
fun Pip(look: PipLook, modifier: Modifier = Modifier) {
    val seconds = rememberSceneSeconds()
    Canvas(modifier.aspectRatio(1f)) {
        val scale = size.minDimension / PIP_EXTENT
        withTransform({
            translate(size.width / 2, size.height / 2)
            scale(scale, scale, Offset.Zero)
        }) {
            drawPip(look, seconds.value)
        }
    }
}

/** Width and height of everything Pip can draw, glow included, in design units. Pip's body has a radius of 30. */
internal const val PIP_EXTENT = 124f

/** Draws Pip centred on the origin, in design units. [seconds] drives breathing and the turning rays. */
internal fun DrawScope.drawPip(look: PipLook, seconds: Float) {
    if (look.glow) {
        val glow = if (look.moon) Illustration.moonGlow.copy(alpha = 0.4f) else Illustration.sunLight.copy(alpha = 0.55f)
        drawCircle(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), Offset.Zero, 62f), 62f, Offset.Zero)
    }
    if (look.rays && !look.moon) {
        rotate(seconds * 6f, Offset.Zero) { // one turn a minute
            repeat(12) { ray ->
                rotate(ray * 30f, Offset.Zero) {
                    drawLine(Illustration.sunLight, Offset(0f, -38f), Offset(0f, -48f), strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
        }
    }
    // Breathes in to 104.5% and out again every 4.5 s.
    val breath = 1f + 0.0225f * (1f - cos(2f * PI.toFloat() * seconds / 4.5f))
    scale(breath, Offset.Zero) {
        if (look.moon) drawMoonFace() else drawSunFace(look.mood)
        if (look.nightcap) drawNightcap()
    }
}

private fun DrawScope.drawSunFace(mood: PipMood) {
    val ink = Illustration.sunInk
    drawCircle(Illustration.sun, 30f, Offset.Zero)
    drawCircle(Illustration.sunLight.copy(alpha = 0.75f), 10f, Offset(-9f, -10f))
    when (mood) {
        PipMood.OPEN, PipMood.SHADES -> {
            drawCircle(ink, 3f, Offset(-9f, -3f))
            drawCircle(ink, 3f, Offset(9f, -3f))
        }
        PipMood.HAPPY -> drawPath(HappyEyes, ink, style = line(2.6f))
        PipMood.ASLEEP, PipMood.YAWNING -> drawPath(ClosedEyes, ink, style = line(2.6f))
    }
    drawCheeks(Illustration.sunCheek.copy(alpha = 0.75f))
    if (mood == PipMood.YAWNING) {
        drawOval(ink, topLeft = Offset(-3.75f, 5f), size = Size(7.5f, 10f))
    } else {
        drawPath(Smile, ink, style = line(2.4f))
    }
    if (mood == PipMood.SHADES) {
        drawRoundRect(Illustration.shades, Offset(-20f, -9f), Size(17f, 11f), CornerRadius(4f))
        drawRoundRect(Illustration.shades, Offset(3f, -9f), Size(17f, 11f), CornerRadius(4f))
        drawPath(ShadesFrame, Illustration.shades, style = line(2.4f))
        drawPath(ShadesGlint, Illustration.glint.copy(alpha = 0.8f), style = line(1.6f))
    }
}

private fun DrawScope.drawMoonFace() {
    drawCircle(Illustration.moon, 30f, Offset.Zero)
    drawCircle(Illustration.moonCrater, 5f, Offset(12f, -15f))
    drawCircle(Illustration.moonCrater, 3.5f, Offset(19f, 6f))
    drawCircle(Illustration.moonCrater, 3f, Offset(-17f, 15f))
    drawCircle(Illustration.moonLight.copy(alpha = 0.7f), 9f, Offset(-9f, -10f))
    drawPath(ClosedEyes, Illustration.moonInk, style = line(2.6f))
    drawCheeks(Illustration.moonCheek.copy(alpha = 0.6f))
    drawPath(Smile, Illustration.moonInk, style = line(2.4f))
}

private fun DrawScope.drawCheeks(color: Color) {
    drawCircle(color, 4.5f, Offset(-18f, 7f))
    drawCircle(color, 4.5f, Offset(18f, 7f))
}

private fun DrawScope.drawNightcap() {
    drawPath(Nightcap, Illustration.nightcap)
    drawPath(NightcapStripe, Illustration.nightcapTrim.copy(alpha = 0.7f), style = line(3f))
    drawRoundRect(Illustration.nightcapTrim, Offset(-29f, -19f), Size(58f, 9f), CornerRadius(4.5f))
    drawCircle(Illustration.nightcapTrim, 6.5f, Offset(42f, -38f))
}

private fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round)

internal fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

// Shapes from the design canvas, in design units around Pip's centre.
private val HappyEyes = svgPath("M-15 -1 q5 -6 10 0M5 -1 q5 -6 10 0")
private val ClosedEyes = svgPath("M-15 -2 q5 5 10 0M5 -2 q5 5 10 0")
private val Smile = svgPath("M-5 10 q5 4 10 0")
private val ShadesFrame = svgPath("M-3 -5 h6M-20 -6 l-8 -3M20 -6 l8 -3")
private val ShadesGlint = svgPath("M-16 -6 l4 -1M7 -6 l4 -1")
private val Nightcap = svgPath("M-26 -14 C-22 -40 12 -52 40 -38 C30 -34 26 -24 26 -14 Z")
private val NightcapStripe = svgPath("M-12 -32 C2 -40 18 -42 30 -38")
