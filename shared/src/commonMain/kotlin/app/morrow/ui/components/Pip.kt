package app.morrow.ui.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import kotlin.math.PI
import kotlin.math.cos

/**
 * Pip on its own, scaled to fit. With a [snoreColor], z's drift up to its right.
 * Pip is decorative, so it adds nothing for screen readers.
 */
@Composable
fun Pip(look: PipLook, modifier: Modifier = Modifier, snoreColor: Color? = null) {
    val seconds = rememberSceneSeconds()
    val zs = if (snoreColor != null) rememberZs(snoreColor, sizes = listOf(17f, 13f, 11f)) else emptyList()
    Canvas(modifier.aspectRatio(1f)) {
        val scale = size.minDimension / PIP_EXTENT
        withTransform({
            translate(size.width / 2, size.height / 2)
            scale(scale, scale, Offset.Zero)
        }) {
            drawPip(look, seconds.value)
            if (zs.isNotEmpty()) drawZs(zs, seconds.value, SnoreZs)
        }
    }
}

/** Where a snoring Pip's z's start, to its upper right. */
private val SnoreZs = listOf(Offset(32f, -22f), Offset(41f, -36f), Offset(49f, -45f))

/** Italic z's measured at [sizes] in design units, for drawing inside a design-unit transform. */
@Composable
internal fun rememberZs(color: Color, sizes: List<Float>): List<TextLayoutResult> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MorrowTheme.type.title.copy(fontStyle = FontStyle.Italic, color = color)
    return remember(measurer, style, density, sizes) {
        sizes.map { size -> measurer.measure("z", style.copy(fontSize = with(density) { size.toSp() })) }
    }
}

/** Z's drifting up and to the right from [starts], fading in and out, each 1.2 s behind the last. */
internal fun DrawScope.drawZs(zs: List<TextLayoutResult>, seconds: Float, starts: List<Offset>) {
    zs.forEachIndexed { i, layout ->
        val elapsed = seconds - i * 1.2f
        if (elapsed < 0f) return@forEachIndexed
        val p = (elapsed / 3.6f) % 1f
        val alpha = if (p < 0.25f) EaseOut.transform(p / 0.25f) else 1f - EaseOut.transform((p - 0.25f) / 0.75f)
        val drift = EaseOut.transform(p)
        val start = starts[i]
        drawText(layout, topLeft = Offset(start.x + 16f * drift, start.y - 38f * drift - layout.firstBaseline), alpha = alpha)
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
        PipMood.DELIGHTED -> {
            drawCircle(ink, 2.7f, Offset(-9.2f, -2.5f))
            drawCircle(ink, 2.7f, Offset(9.2f, -2.5f))
            drawCircle(Illustration.glint, 0.85f, Offset(-8.3f, -3.5f))
            drawCircle(Illustration.glint, 0.85f, Offset(10f, -3.5f))
        }
    }
    drawCheeks(Illustration.sunCheek.copy(alpha = 0.75f))
    if (mood == PipMood.YAWNING) {
        drawOval(ink, topLeft = Offset(-3.75f, 5f), size = Size(7.5f, 10f))
    } else if (mood == PipMood.DELIGHTED) {
        drawPath(Grin, ink)
        drawPath(Tongue, Illustration.sunCheek)
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

/** Draws [block] as a group at [alpha], so overlapping shapes fade together. [bounds] must hold everything drawn. */
internal fun DrawScope.withAlpha(alpha: Float, bounds: Rect, block: DrawScope.() -> Unit) {
    drawContext.canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

private fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round)

internal fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

// Shapes from the design canvas, in design units around Pip's centre.
private val HappyEyes = svgPath("M-15 -1 q5 -6 10 0M5 -1 q5 -6 10 0")
private val ClosedEyes = svgPath("M-15 -2 q5 5 10 0M5 -2 q5 5 10 0")
private val Smile = svgPath("M-5 10 q5 4 10 0")
private val Grin = svgPath("M-7.5 7.5 q7.5 8.3 15 0Z")
private val Tongue = svgPath("M-3.3 10.8 q3.3 2.5 6.7 0Z")
private val ShadesFrame = svgPath("M-3 -5 h6M-20 -6 l-8 -3M20 -6 l8 -3")
private val ShadesGlint = svgPath("M-16 -6 l4 -1M7 -6 l4 -1")
private val Nightcap = svgPath("M-26 -14 C-22 -40 12 -52 40 -38 C30 -34 26 -24 26 -14 Z")
private val NightcapStripe = svgPath("M-12 -32 C2 -40 18 -42 30 -38")
