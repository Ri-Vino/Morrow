package app.morrow.ui.timers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.rememberSceneSeconds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A timer's round window on the sky: Pip sinks from midday towards the hills and the sky turns to dusk as the timer
 * runs down, while the ring round the window empties. Pip dozes while it's paused and bounces once time is up. It
 * moves smoothly between the seconds. Decorative, so screen readers skip it.
 */
@Composable
internal fun TimerWindow(sky: TimerSky, state: TimerState, modifier: Modifier = Modifier) {
    val track = MorrowTheme.colors.surface2
    val counting = state == TimerState.RUNNING
    // Glides through each second while running; jumps when paused, resumed or given another minute.
    val progress by animateFloatAsState(sky.progress, if (counting) tween(1000, easing = LinearEasing) else tween(300))
    val shown = timerSky(progress)
    val seconds by rememberSceneSeconds()
    val mood = when {
        state == TimerState.PAUSED -> PipMood.ASLEEP
        !state.counts -> PipMood.DELIGHTED
        shown.sleepy -> PipMood.YAWNING
        else -> PipMood.OPEN
    }

    Canvas(modifier) {
        val ring = 6.dp.toPx()
        val radius = size.minDimension / 2f - ring
        val window = Path().apply { addOval(Rect(center, radius - ring / 2f)) }
        clipPath(window) {
            drawRect(shown.sky)
            if (shown.starAlpha > 0f) {
                WindowStars.forEach { (x, y) ->
                    drawCircle(Illustration.starlight.copy(alpha = shown.starAlpha), 1.2.dp.toPx(), Offset(size.width * x, size.height * y))
                }
            }
            // Pip crosses from upper left to lower right, setting behind the hills.
            val bounce = if (state.counts) 0f else abs(sin(seconds * PI.toFloat() * 1.6f)) * 6.dp.toPx()
            val pip = Offset(size.width * (0.3f + 0.4f * progress), size.height * (0.34f + 0.42f * progress * progress) - bounce)
            translate(pip.x, pip.y) {
                scale(radius / 30f * 0.42f, Offset.Zero) { drawPip(PipLook(mood, rays = !state.counts), seconds) }
            }
            drawOval(shown.hillBack, Offset(-size.width * 0.2f, size.height * 0.7f), Size(size.width * 0.9f, size.height * 0.6f))
            drawOval(shown.hillFront, Offset(size.width * 0.25f, size.height * 0.76f), Size(size.width * 1.0f, size.height * 0.6f))
        }
        drawCircle(track, radius, style = Stroke(ring))
        val left = 1f - progress
        if (left > 0f) {
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(0f to Illustration.sun, left to Illustration.sunLight, center = center),
                    startAngle = 0f,
                    sweepAngle = left * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(ring, cap = StrokeCap.Round),
                )
            }
            // The gradient wraps round, so the start's rounded end is painted on in its own colour.
            drawCircle(Illustration.sun, ring / 2f, Offset(center.x, center.y - radius))
        }
    }
}

/** Running or paused, rather than ended in one way or another. */
private val TimerState.counts: Boolean get() = this == TimerState.RUNNING || this == TimerState.PAUSED

/** Where the stars come out in the window, as fractions of its size. */
private val WindowStars = listOf(0.3f to 0.22f, 0.55f to 0.14f, 0.72f to 0.3f, 0.42f to 0.4f)
