package app.morrow.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Fades in while rising 16 dp into place. Reads [progress] while drawing, so it doesn't recompose every frame. */
internal fun Modifier.fadeUpIn(progress: () -> Float) = graphicsLayer {
    val p = progress()
    alpha = p
    translationY = 16.dp.toPx() * (1f - p)
}
