package app.morrow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Whether scenes may move. Outside a [RestingScenes], as on the ring screen, they always may. */
internal val LocalScenesAwake = compositionLocalOf { true }

/**
 * A box whose scenes rest: once nothing has touched the screen for [after], they hold still where they are, so the
 * phone draws nothing until the next touch, or the app coming back to the front, sets them going again from there.
 * Scenes with an intro, the ring, Morning and timer-done screens, never rest.
 */
@Composable
fun RestingScenes(modifier: Modifier = Modifier, after: Duration = 1.minutes, content: @Composable BoxScope.() -> Unit) {
    var touches by remember { mutableIntStateOf(0) }
    var awake by remember { mutableStateOf(true) }
    LaunchedEffect(touches) {
        awake = true
        delay(after)
        awake = false
    }
    LifecycleResumeEffect(Unit) {
        touches++
        onPauseOrDispose {}
    }
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Watches touches on their way down without taking them from what's under them.
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) touches++
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalScenesAwake provides awake) { content() }
    }
}
