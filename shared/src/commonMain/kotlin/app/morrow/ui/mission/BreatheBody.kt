package app.morrow.ui.mission

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.missions.BreatheMission
import app.morrow.missions.BreatheState
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.svgPath

@Composable
internal fun BreatheBody(state: BreatheState, mission: BreatheMission) {
    val colors = MorrowTheme.colors
    // Runs until the breath ends or Pip is let go, which restarts this effect.
    LaunchedEffect(state.holding) {
        if (state.holding) while (true) withFrameNanos { mission.tick() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BasicText(breatheLabel(state), style = MorrowTheme.type.title.copy(color = colors.text), modifier = Modifier.heightIn(min = 32.dp))
        HoldButton(state.hold, mission)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(state.count) { i -> BreathDot(on = i < state.breaths) }
        }
        BasicText(
            breatheHint(state),
            style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

/** Pip in a ring that fills over one breath while held. It keeps the touch even if the finger drifts. */
@Composable
private fun HoldButton(hold: Float, mission: BreatheMission) {
    val colors = MorrowTheme.colors
    Canvas(
        Modifier
            .size(240.dp)
            .semantics {
                contentDescription = "Press and hold to breathe"
                role = Role.Button
            }
            .pointerInput(mission) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    mission.press()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        mission.release()
                    }
                }
            },
    ) {
        scale(size.minDimension / 240f, Offset.Zero) {
            val centre = Offset(120f, 120f)
            drawCircle(colors.surface, 108f, centre)
            drawCircle(colors.line, 108f, centre, style = Stroke(8f))
            if (hold > 0f) {
                drawArc(
                    colors.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * hold,
                    useCenter = false,
                    topLeft = Offset(12f, 12f),
                    size = Size(216f, 216f),
                    style = Stroke(8f, cap = StrokeCap.Round),
                )
            }
            translate(120f, 120f) {
                scale(0.62f + 0.38f * hold, Offset.Zero) {
                    val ink = Illustration.sunInk
                    drawCircle(Illustration.sunLight.copy(alpha = 0.22f), 70f, Offset.Zero)
                    drawCircle(Illustration.sun, 56f, Offset.Zero)
                    drawCircle(Illustration.sunLight.copy(alpha = 0.7f), 16f, Offset(-16f, -18f))
                    drawPath(CalmEyes, ink, style = Stroke(4f, cap = StrokeCap.Round))
                    drawCircle(Illustration.sunCheek.copy(alpha = 0.7f), 7f, Offset(-30f, 12f))
                    drawCircle(Illustration.sunCheek.copy(alpha = 0.7f), 7f, Offset(30f, 12f))
                    drawPath(CalmSmile, ink, style = Stroke(4f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun BreathDot(on: Boolean) {
    val colors = MorrowTheme.colors
    val color by animateColorAsState(if (on) colors.accent else colors.line, tween(400))
    val scale by animateFloatAsState(if (on) 1.25f else 1f, tween(400))
    Box(Modifier.size(10.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(color, CircleShape))
}

// Shapes from the design canvas.
private val CalmEyes = svgPath("M-26 -4 q8 7 16 0M10 -4 q8 7 16 0")
private val CalmSmile = svgPath("M-8 16 q8 7 16 0")
