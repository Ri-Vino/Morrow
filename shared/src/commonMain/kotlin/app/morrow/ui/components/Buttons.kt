package app.morrow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Clickable that shrinks a little while pressed, like the design's buttons. */
fun Modifier.pressable(onClick: () -> Unit, role: Role = Role.Button, onClickLabel: String? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(140))
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, role = role, onClickLabel = onClickLabel, onClick = onClick)
}

/**
 * Like [pressable], but holding it repeats [onPress]: once at the touch, then again and again after a moment, faster the
 * longer it's held. TalkBack's double tap presses it once.
 */
fun Modifier.repeatingPressable(onPress: () -> Unit, onClickLabel: String? = null): Modifier = composed {
    val press by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(140))
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick(onClickLabel) { press(); true }
        }
        .pointerInput(Unit) {
            coroutineScope {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    press()
                    val repeating = launch {
                        delay(REPEAT_AFTER_MS)
                        var interval = REPEAT_SLOWEST_MS
                        while (true) {
                            press()
                            delay(interval)
                            interval = (interval * 4 / 5).coerceAtLeast(REPEAT_FASTEST_MS)
                        }
                    }
                    waitForUpOrCancellation()
                    repeating.cancel()
                    pressed = false
                }
            }
        }
}

private const val REPEAT_AFTER_MS = 400L
private const val REPEAT_SLOWEST_MS = 160L
private const val REPEAT_FASTEST_MS = 45L

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, iconAfter: Boolean = false) {
    val colors = MorrowTheme.colors
    PillButton(text, onClick, colors.accent, colors.onAccent, modifier.fillMaxWidth(), height = 56.dp, icon = icon, iconAfter = iconAfter)
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    PillButton(text, onClick, colors.surface, colors.text, modifier.fillMaxWidth(), height = 56.dp, border = colors.line)
}

/** A rounded button; [height] 44 for small inline actions, 56 for main ones. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    icon: String? = null,
    iconAfter: Boolean = false,
    iconTint: Color = content,
    border: Color? = null,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val type = if (height >= 56.dp) MorrowTheme.type.button else MorrowTheme.type.bodyStrong
    Row(
        modifier = modifier
            .heightIn(min = height)
            .pressable(onClick)
            .background(background, CircleShape)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && !iconAfter) MorrowIcon(icon, iconTint, size = 22.dp, strokeWidth = 2.6f)
        BasicText(text, style = type.copy(color = content))
        if (icon != null && iconAfter) MorrowIcon(icon, iconTint, size = 22.dp, strokeWidth = 2.6f)
    }
}
