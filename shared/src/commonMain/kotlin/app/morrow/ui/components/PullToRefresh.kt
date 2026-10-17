package app.morrow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

/**
 * [content], which scrolls, with pull-to-refresh: pulling down past its top far enough and letting go calls
 * [onRefresh], and a spinning arrow shows while [refreshing]. Screen readers get a [label] action instead.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val threshold = with(LocalDensity.current) { PULL_TO_REFRESH.toPx() }
    val refresh by rememberUpdatedState(onRefresh)
    val busy by rememberUpdatedState(refreshing)
    var pulled by remember { mutableFloatStateOf(0f) }
    val pull = remember(threshold) {
        object : NestedScrollConnection {
            // Scrolling back up takes back the pull before the content scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || pulled <= 0f) return Offset.Zero
                val used = maxOf(available.y, -pulled)
                pulled += used
                return Offset(0f, used)
            }

            // What the content can't scroll, at its top, pulls, a little stiffer than the finger.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                pulled = (pulled + available.y * RESISTANCE).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f) return Velocity.Zero
                if (pulled >= threshold && !busy) refresh()
                animate(pulled, 0f) { value, _ -> pulled = value }
                return available
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .nestedScroll(pull)
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { refresh(); true }) },
    ) {
        content()
        val shown = if (refreshing) threshold else pulled
        if (shown > 0f) RefreshArrow(shown, threshold, refreshing, Modifier.align(Alignment.TopCenter))
    }
}

/** The arrow that comes down as the content's pulled, turning with it, and spins while refreshing. Decorative. */
@Composable
private fun RefreshArrow(shown: Float, threshold: Float, refreshing: Boolean, modifier: Modifier) {
    val colors = MorrowTheme.colors
    val spin = if (refreshing) {
        val turning = rememberInfiniteTransition()
        turning.animateFloat(0f, 360f, infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart)).value
    } else {
        shown / threshold * 270f
    }
    Box(
        modifier = modifier
            .statusBarsPadding()
            .graphicsLayer {
                translationY = shown - size.height
                alpha = (shown / threshold).coerceIn(0f, 1f)
                rotationZ = spin
            }
            .size(40.dp)
            .shadow(6.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        MorrowIcon(Icons.Refresh, colors.hi, size = 22.dp, strokeWidth = 2.4f)
    }
}

private val PULL_TO_REFRESH = 80.dp
private const val RESISTANCE = 0.5f
private const val SPIN_MILLIS = 900
