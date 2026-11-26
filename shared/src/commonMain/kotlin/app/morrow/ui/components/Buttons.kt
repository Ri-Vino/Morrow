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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
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

/**
 * The main action on a screen. Button labels, like every label in the app, are in sentence case ("Save alarm", not
 * "Save Alarm") on both platforms: one shared voice, Android's own convention, and calmer than title case for a
 * friendly app. Small uppercase labels are kept for section eyebrows only. While not [enabled] it keeps its colour,
 * faded, so it still reads as the main action that isn't ready yet.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    icon: String? = null,
    iconAfter: Boolean = false,
) {
    val colors = MorrowTheme.colors
    MorrowButton(
        text = text,
        onClick = { if (enabled) onClick() },
        background = colors.primary,
        content = colors.onPrimary,
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { if (!enabled) disabled() },
        height = height,
        icon = icon,
        iconAfter = iconAfter,
    )
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    MorrowButton(text, onClick, colors.surface, colors.text, modifier.fillMaxWidth(), height = 56.dp, border = colors.line)
}

/**
 * A button; [height] 44 for chips and small inline actions, which are pills, and 56 or more for main ones, which take
 * the control radius.
 */
@Composable
fun MorrowButton(
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
    val large = height >= 56.dp
    val type = if (large) MorrowTheme.type.button else MorrowTheme.type.bodyStrong
    val shape = if (large) ControlShape else CircleShape
    Row(
        modifier = modifier
            .heightIn(min = height)
            .pressable(onClick)
            .background(background, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && !iconAfter) MorrowIcon(icon, iconTint, size = 22.dp, strokeWidth = 2.6f)
        BasicText(text, style = type.copy(color = content))
        if (icon != null && iconAfter) MorrowIcon(icon, iconTint, size = 22.dp, strokeWidth = 2.6f)
    }
}

/** The corner of buttons, fields and other controls that aren't small enough to be pills. */
val ControlShape = RoundedCornerShape(16.dp)

private const val DISABLED_ALPHA = 0.4f
