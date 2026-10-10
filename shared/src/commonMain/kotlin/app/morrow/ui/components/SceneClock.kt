package app.morrow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/**
 * Seconds since the scene appeared, advancing with each frame. It only runs while the scene is on screen,
 * and stays at [still] when the system's "remove animations" setting is on.
 * Read it inside a draw block so only drawing, not composition, repeats each frame.
 *
 * The scenes' loops are slow, so after the first [fullRateFor] seconds it advances about 30 times a second
 * rather than at the screen's full refresh rate, which on a 120 Hz phone is a quarter of the frames.
 */
@Composable
internal fun rememberSceneSeconds(still: Float = 0f, fullRateFor: Float = 0f): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
            seconds.floatValue = still
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        val fullRateUntil = start + (fullRateFor * NANOS_PER_SECOND).toLong()
        var shown = start
        while (true) {
            val showing = withFrameNanos { frame ->
                val due = frame < fullRateUntil || frame - shown >= SLOW_FRAME_GAP_NANOS
                if (due) {
                    seconds.floatValue = (frame - start) / NANOS_PER_SECOND
                    shown = frame
                }
                due
            }
            // Sleeps through most of the gap rather than waking on every refresh. One 60 Hz frame is the most it can
            // sleep without missing the next due frame.
            if (showing && shown >= fullRateUntil) delay(SLOW_FRAME_SLEEP_MILLIS)
        }
    }
    return seconds
}

private const val NANOS_PER_SECOND = 1_000_000_000f

// 30 fps is a 33.3 ms gap; accepting 29 ms keeps 90, 120 and 144 Hz screens, whose frames don't land exactly on it, at about 30.
private const val SLOW_FRAME_GAP_NANOS = 29_000_000L
private const val SLOW_FRAME_SLEEP_MILLIS = 16L
