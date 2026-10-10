package app.morrow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.SkyCurve
import app.morrow.theme.mix
import app.morrow.theme.sunElevation
import app.morrow.ui.edit.DIAL_STEP_MINUTES
import app.morrow.ui.edit.dialMinute
import app.morrow.ui.hhmm
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DIAL = 256f
private const val CENTRE = DIAL / 2
private const val RING_RADIUS = 110f

/**
 * The 24-hour sky dial from the design: drag the sun or moon round the ring to pick a time, snapped to 5 minutes.
 * The mini sky inside shows the chosen time. Screen readers treat it as a slider.
 */
@Composable
fun SkyDial(minuteOfDay: Int, onMinuteChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val haptics = LocalHapticFeedback.current
    val latestMinute by rememberUpdatedState(minuteOfDay)
    val latestChange by rememberUpdatedState(onMinuteChange)
    var dragging by remember { mutableStateOf(false) }
    val seconds = rememberSceneSeconds()

    val elevation = sunElevation(minuteOfDay.toFloat()).toFloat()
    val sunUp = elevation >= 0f
    val sky by animateColorAsState(SkyCurve.at(minuteOfDay.toFloat()), tween(350))
    val measurer = rememberTextMeasurer()
    val labelStyle = MorrowTheme.type.label.copy(color = colors.muted, letterSpacing = MorrowTheme.type.caption.letterSpacing)
    val labels = remember(measurer, labelStyle) { listOf("00", "06", "12", "18").map { measurer.measure(it, labelStyle) } }

    Canvas(
        modifier
            .size(256.dp)
            .semantics {
                contentDescription = "Alarm time"
                stateDescription = hhmm(minuteOfDay / 60, minuteOfDay % 60)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = minuteOfDay.toFloat(),
                    range = 0f..(24 * 60f - DIAL_STEP_MINUTES),
                    steps = 24 * 60 / DIAL_STEP_MINUTES - 2,
                )
                setProgress { target ->
                    latestChange((target.roundToInt() / DIAL_STEP_MINUTES * DIAL_STEP_MINUTES).coerceIn(0, 24 * 60 - DIAL_STEP_MINUTES))
                    true
                }
            }
            .pointerInput(Unit) {
                fun pick(position: Offset) {
                    val minute = dialMinute(position.x - size.width / 2f, position.y - size.height / 2f)
                    if (minute != latestMinute) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestChange(minute)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    pick(down.position)
                    drag(down.id) { change ->
                        pick(change.position)
                        change.consume()
                    }
                    dragging = false
                }
            },
    ) {
        val unit = size.minDimension / DIAL
        val t = seconds.value
        scale(unit, Offset.Zero) {
            // The mini sky, a disc 196 across, with Pip at the chosen time.
            translate(30f, 30f) {
                clipPath(MiniSky) {
                    drawRect(sky, size = Size(196f, 196f))
                    val stars = (-elevation * 2.5f).coerceIn(0f, 1f)
                    if (stars > 0f) DialStars.forEach { (x, y, r) -> drawCircle(Illustration.starlight.copy(alpha = stars), r, Offset(x, y)) }
                    translate(98f, 128f - min(1f, abs(elevation)) * 78f) {
                        if (sunUp) {
                            drawCircle(Illustration.sunLight.copy(alpha = 0.25f), 34f, Offset.Zero)
                            scale(20f / 30f, Offset.Zero) { drawPip(PipLook(PipMood.OPEN, glow = false), t) }
                        } else {
                            drawCircle(Illustration.moonGlow.copy(alpha = 0.15f), 32f, Offset.Zero)
                            scale(19f / 30f, Offset.Zero) { drawPip(PipLook(PipMood.ASLEEP, moon = true, glow = false), t) }
                        }
                    }
                    drawPath(DialHillBack, mix(sky, Illustration.ground, 0.42f))
                    drawPath(DialHillFront, mix(sky, Illustration.ground, 0.66f))
                }
            }
            // The ring, with a tick every hour except the four labelled ones.
            drawCircle(colors.surface, RING_RADIUS, Offset(CENTRE, CENTRE), style = Stroke(28f))
            for (hour in 1 until 24) {
                if (hour % 6 == 0) continue
                rotate(hour * 15f, Offset(CENTRE, CENTRE)) {
                    drawLine(colors.line, Offset(CENTRE, 15f), Offset(CENTRE, 21f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
        }
        // Labels are drawn unscaled so they stay crisp at 12 sp.
        listOf(Offset(128f, 15f), Offset(238f, 128f), Offset(128f, 241f), Offset(18f, 128f)).forEachIndexed { i, at ->
            val layout = labels[i]
            drawText(layout, topLeft = Offset(at.x * unit - layout.size.width / 2f, at.y * unit - layout.size.height / 2f))
        }
        scale(unit, Offset.Zero) {
            val angle = minuteOfDay / (24 * 60f) * 2f * PI.toFloat()
            translate(CENTRE + RING_RADIUS * sin(angle), CENTRE - RING_RADIUS * cos(angle)) {
                if (dragging) {
                    val p = (t / 2.4f) % 1f
                    val grow = 0.8f + 0.7f * EaseOut.transform(p)
                    drawCircle(Illustration.sun.copy(alpha = 0.6f * (1f - EaseOut.transform(p))), 22f * grow, Offset.Zero)
                }
                drawCircle(if (sunUp) Illustration.sunLight else Illustration.moon, 18f, Offset.Zero)
                drawCircle(colors.bg, 18f, Offset.Zero, style = Stroke(3f))
                if (sunUp) {
                    drawCircle(Illustration.sunInk, 1.9f, Offset(-4.5f, -2f))
                    drawCircle(Illustration.sunInk, 1.9f, Offset(4.5f, -2f))
                    drawPath(KnobSmile, Illustration.sunInk, style = Stroke(1.8f, cap = StrokeCap.Round))
                } else {
                    drawPath(KnobMoon, Illustration.sunInk)
                }
            }
        }
    }
}

private val MiniSky = Path().apply { addOval(Rect(0f, 0f, 196f, 196f)) }
private val DialStars = listOf(Triple(40f, 50f, 1.4f), Triple(78f, 30f, 1.1f), Triple(130f, 44f, 1.5f), Triple(160f, 76f, 1.1f), Triple(56f, 88f, 1.1f), Triple(110f, 80f, 1.2f))
private val DialHillBack = svgPath("M0 128 C50 112 110 118 196 122 L196 196 L0 196 Z")
private val DialHillFront = svgPath("M0 152 C70 140 130 154 196 142 L196 196 L0 196 Z")
private val KnobSmile = svgPath("M-4 3.5 q4 3.5 8 0")
private val KnobMoon = svgPath("M2 -8 a8 8 0 1 0 6 12 a6.5 6.5 0 1 1 -6 -12 z")
