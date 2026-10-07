package app.morrow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext

/**
 * Seconds since the scene appeared, advancing with each frame. It only runs while the scene is on screen,
 * and stays at [still] when the system's "remove animations" setting is on.
 * Read it inside a draw block so only drawing, not composition, repeats each frame.
 */
@Composable
internal fun rememberSceneSeconds(still: Float = 0f): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
            seconds.floatValue = still
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { seconds.floatValue = (it - start) / 1_000_000_000f }
        }
    }
    return seconds
}
