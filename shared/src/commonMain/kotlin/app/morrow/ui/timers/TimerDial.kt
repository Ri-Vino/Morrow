package app.morrow.ui.timers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.pressable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.time.Duration.Companion.minutes

/**
 * The wind-up dial: drag Pip round the ring like a kitchen timer, an hour a lap, and the ring fills behind him. Each
 * full hour leaves a bead inside the ring. A new [entry] from a quick chip sweeps him round to it. Tapping the time in
 * the middle calls [onType]. TalkBack can add or take off a minute.
 */
@Composable
internal fun TimerDial(entry: TimerEntry, onWind: (TimerEntry) -> Unit, onType: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(entry)
    val wind by rememberUpdatedState(onWind)
    var dragging by remember { mutableStateOf(false) }
    // Hours, so one turn of the dial is 1.
    val turns = remember { Animatable(entry.turns) }
    LaunchedEffect(entry, dragging) {
        if (dragging) turns.snapTo(entry.turns) else turns.animateTo(entry.turns, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
    }
    val knobScale by animateFloatAsState(if (dragging) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy))
    val minutes = entry.duration.inWholeMinutes.toInt()

    Box(
        modifier = modifier
            .size(288.dp)
            .semantics {
                contentDescription = entry.label
                customActions = listOf(
                    CustomAccessibilityAction("One minute more") { onWind(TimerEntry.of((minutes + 1).coerceAtMost(MAX_DIAL_MINUTES).minutes)); true },
                    CustomAccessibilityAction("One minute less") { onWind(TimerEntry.of((minutes - 1).coerceAtLeast(0).minutes)); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var grabbed = false
                    detectDragGestures(
                        onDragStart = { at ->
                            // The middle is for tapping the time; winding starts on the ring.
                            grabbed = hypot(at.x - size.width / 2f, at.y - size.height / 2f) > size.width * 0.28f
                            dragging = grabbed
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            if (!grabbed) return@detectDragGestures
                            change.consume()
                            val now = latest.duration.inWholeMinutes.toInt()
                            val next = windDial(now, fractionAt(change.position, size.width / 2f, size.height / 2f))
                            if (next != now || latest.duration.inWholeSeconds % 60 != 0L) {
                                if (next != now) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                wind(TimerEntry.of(next.minutes))
                            }
                        },
                    )
                },
        ) {
            drawDial(turns.value, knobScale, dragging, colors.surface, colors.surface2, colors.line, colors.muted)
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .pressable(onType, onClickLabel = "Type the length")
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                entry.clock,
                style = (if (entry.hoursTyped) type.display else type.displayLarge).copy(
                    color = if (entry.canStart) colors.text else colors.muted,
                    fontFeatureSettings = "tnum",
                ),
            )
            BasicText(
                if (entry.canStart) "Tap to type it" else "Wind Pip round",
                style = type.caption.copy(color = colors.muted, textAlign = TextAlign.Center),
            )
        }
    }
}

/** How far round the dial [at] is, from the top and clockwise, 0 to 1. */
private fun fractionAt(at: Offset, centerX: Float, centerY: Float): Float {
    val angle = atan2(at.x - centerX, centerY - at.y)
    return ((angle / (2 * PI)).toFloat() + 1f) % 1f
}

/** The entry as hours, for the dial's turns. */
private val TimerEntry.turns: Float get() = duration.inWholeSeconds / 3600f

/** "25:00", or "1:25:00" from an hour. */
private val TimerEntry.clock: String
    get() = if (hoursTyped) "${hoursText.trimStart('0').ifEmpty { "0" }}:$minutesText:$secondsText" else "$minutesText:$secondsText"

private fun DrawScope.drawDial(
    turns: Float,
    knobScale: Float,
    dragging: Boolean,
    face: Color,
    track: Color,
    tick: Color,
    strongTick: Color,
) {
    val ring = 18.dp.toPx()
    val knob = 26.dp.toPx()
    val radius = size.minDimension / 2f - knob
    val laps = floor(turns).toInt()
    val part = turns - laps

    drawCircle(face, radius + ring / 2f + 8.dp.toPx())
    // Minute ticks inside the ring, longer every five and darker every quarter hour.
    repeat(60) { i ->
        val long = i % 5 == 0
        val inner = radius - ring / 2f - (if (long) 14 else 9).dp.toPx()
        val outer = radius - ring / 2f - 4.dp.toPx()
        val angle = i / 60f * 2f * PI.toFloat()
        drawLine(
            if (i % 15 == 0) strongTick else tick,
            center + polar(inner, angle),
            center + polar(outer, angle),
            strokeWidth = (if (long) 2.5f else 1.5f).dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawCircle(track, radius, style = Stroke(ring))
    // A full lap stays lit underneath the next one.
    if (laps > 0) drawCircle(Illustration.sun.copy(alpha = 0.45f), radius, style = Stroke(ring))
    if (part > 0f) {
        rotate(-90f) {
            drawArc(
                brush = Brush.sweepGradient(0f to Illustration.sunLight, part to Illustration.sun, center = center),
                startAngle = 0f,
                sweepAngle = part * 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(ring, cap = StrokeCap.Round),
            )
        }
        // The gradient wraps round, so the start's rounded end is painted on in its own colour.
        drawCircle(Illustration.sunLight, ring / 2f, center + polar(radius, 0f))
    }
    // A bead for each whole hour, round an inner ring like hours on a clock.
    val beads = laps.coerceAtMost(12)
    repeat(beads) { i ->
        drawCircle(Illustration.sun, 4.dp.toPx(), center + polar(radius - ring - 26.dp.toPx(), i / 12f * 2f * PI.toFloat()))
    }

    val at = center + polar(radius, part * 2f * PI.toFloat())
    val look = PipLook(if (turns <= 0f) PipMood.ASLEEP else if (dragging) PipMood.DELIGHTED else PipMood.OPEN, rays = turns > 0f)
    translate(at.x, at.y) {
        // Pip's body is 30 units across in his own drawing; here it fills the knob. His rays turn as he's wound.
        scale(knob / 30f * 0.62f * knobScale, Offset.Zero) { drawPip(look, seconds = turns * 120f) }
    }
}

private fun polar(radius: Float, angle: Float) = Offset(sin(angle) * radius, -cos(angle) * radius)
