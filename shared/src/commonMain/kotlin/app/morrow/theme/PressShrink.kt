package app.morrow.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * The press feedback for every control: it shrinks a little while held, as the design's buttons do, rather than
 * Compose's default grey rectangle, which ignores rounded corners.
 */
internal object PressShrink : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressShrinkNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0
}

private class PressShrinkNode(private val interactions: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            var held = 0
            interactions.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> held++
                    is PressInteraction.Release, is PressInteraction.Cancel -> held = (held - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                launch { scale.animateTo(if (held > 0) PRESSED_SCALE else 1f, tween(140)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val shown = scale.value
        if (shown == 1f) drawContent() else scale(shown) { this@draw.drawContent() }
    }
}

private const val PRESSED_SCALE = 0.96f
