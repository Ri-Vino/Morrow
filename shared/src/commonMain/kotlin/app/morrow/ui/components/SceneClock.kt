package app.morrow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import app.morrow.theme.MorrowTheme
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/**
 * Seconds since the scene appeared, advancing with each frame. It only runs while the scene is on screen,
 * and stays at [still] when the phone asks for less motion: Reduce Motion on iPhone, Remove animations on Android.
 * Read it inside a draw block so only drawing, not composition, repeats each frame.
 *
 * The scenes' loops are slow, so after the first [fullRateFor] seconds it advances about 30 times a second
 * rather than at the screen's full refresh rate, which on a 120 Hz phone is a quarter of the frames. Every scene on
 * screen advances on the same frames, those that start a new 30th of a second, so two scenes together still redraw
 * 30 times a second rather than each on frames of its own.
 */
@Composable
internal fun rememberSceneSeconds(still: Float = 0f, fullRateFor: Float = 0f): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val reduceMotion = MorrowTheme.reduceMotion
    LaunchedEffect(reduceMotion) {
        if (reduceMotion || (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
            seconds.floatValue = still
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        val fullRateUntil = start + (fullRateFor * NANOS_PER_SECOND).toLong()
        var shownSlot = Long.MIN_VALUE
        while (true) {
            val sleep = withFrameNanos { frame ->
                val slot = frame / SLOW_FRAME_GAP_NANOS
                val fullRate = frame < fullRateUntil
                val due = fullRate || slot != shownSlot
                if (due) {
                    seconds.floatValue = (frame - start) / NANOS_PER_SECOND
                    shownSlot = slot
                }
                due && !fullRate
            }
            // Sleeps through most of the gap rather than waking on every refresh. One 60 Hz frame is the most it can
            // sleep without missing the first frame of the next slot.
            if (sleep) delay(SLOW_FRAME_SLEEP_MILLIS)
        }
    }
    return seconds
}

private const val NANOS_PER_SECOND = 1_000_000_000f

// 30 fps. Frames are timed from the same vsync for every scene, so slots of this length line their updates up.
private const val SLOW_FRAME_GAP_NANOS = 33_333_333L
private const val SLOW_FRAME_SLEEP_MILLIS = 16L
