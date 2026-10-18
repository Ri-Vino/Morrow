package app.morrow.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.drawPip
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What holding the new alarm button offers, besides the normal alarm a tap makes. */
enum class QuickKind(val label: String, val spoken: String) {
    TEMPORARY("Temporary", "Temporary alarm"),
    NAP("Nap", "Nap"),
    IN_MINUTES("In X min", "Alarm in a few minutes"),
}

/**
 * Home's new alarm button: Pip as a smiling sun with a small plus. A tap makes a normal alarm. Holding it opens the
 * other kinds as [open] says: the rays spin out, Pip's eyes go wide, and [QuickKind]s pop out in an arc above and to
 * the left. Screen readers get the kinds as actions on the button. With Reduce Motion they fade in rather than pop.
 */
@Composable
fun SunButton(
    open: Boolean,
    onNewAlarm: () -> Unit,
    onOpenChange: (Boolean) -> Unit,
    onPick: (QuickKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    val reduceMotion = MorrowTheme.reduceMotion
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onNewAlarm)
    val openChange by rememberUpdatedState(onOpenChange)
    val isOpen by rememberUpdatedState(open)
    val press by animateFloatAsState(if (pressed && !open) 0.92f else if (open) 1.06f else 1f, spring(dampingRatio = 0.5f))
    val burst by animateFloatAsState(if (open) 1f else 0f, if (reduceMotion) tween(0) else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))

    // The menu's room is only taken while it's open, so the space beside the button stays free to tap otherwise.
    Box(modifier.then(if (open) Modifier.size(MENU_WIDTH, MENU_HEIGHT) else Modifier), contentAlignment = Alignment.BottomEnd) {
        QuickKind.entries.forEachIndexed { i, kind ->
            val shown by animateFloatAsState(
                if (open) 1f else 0f,
                if (reduceMotion) tween(150) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f),
                label = "choice $i",
            )
            if (shown > 0.01f) {
                val (x, y) = ChoiceOffsets[i]
                PillButton(
                    text = kind.label,
                    onClick = { onPick(kind) },
                    background = colors.surface,
                    content = colors.text,
                    border = colors.line,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x, y)
                        .graphicsLayer {
                            alpha = shown.coerceIn(0f, 1f)
                            val grow = if (reduceMotion) 1f else 0.5f + 0.5f * shown
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = TransformOrigin(1f, 1f)
                        }
                        .shadow(8.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow),
                )
            }
        }
        Canvas(
            Modifier
                .size(SUN_SIZE)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "New alarm"
                    onClick { tap(); true }
                    onLongClick("More kinds of alarm") { openChange(!isOpen); true }
                    customActions = QuickKind.entries.map { kind -> CustomAccessibilityAction(kind.spoken) { onPick(kind); true } }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { if (isOpen) openChange(false) else tap() },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            openChange(true)
                        },
                    )
                },
        ) {
            val unit = size.minDimension / SUN_UNITS
            scale(unit, Offset.Zero) {
                translate(SUN_UNITS / 2, SUN_UNITS / 2) {
                    // The rays turn an eighth of a turn and reach further as the menu opens.
                    rotate(45f * burst, Offset.Zero) {
                        repeat(RAYS) { ray ->
                            val angle = 2f * PI.toFloat() * ray / RAYS
                            val direction = Offset(cos(angle), sin(angle))
                            drawLine(
                                Illustration.sunLight,
                                direction * 33f,
                                direction * (40f + 8f * burst),
                                strokeWidth = 4f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    drawCircle(colors.shadow, 29f, Offset(0f, 1.5f))
                    scale(28f / 30f, Offset.Zero) {
                        drawPip(PipLook(if (open) PipMood.DELIGHTED else PipMood.HAPPY, glow = false), seconds = 0f)
                    }
                    // A small plus on the lower right keeps it reading as "add".
                    val badge = Offset(20f, 20f)
                    drawCircle(colors.bg, 11.5f, badge)
                    drawCircle(colors.accent, 9.5f, badge)
                    drawLine(colors.onAccent, badge + Offset(-4.5f, 0f), badge + Offset(4.5f, 0f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                    drawLine(colors.onAccent, badge + Offset(0f, -4.5f), badge + Offset(0f, 4.5f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

private val SUN_SIZE = 76.dp
/** The sun's drawing, rays included, in its own units around its centre: Pip's face is 28 across. */
private const val SUN_UNITS = 100f
private const val RAYS = 12

private val MENU_WIDTH = 260.dp
private val MENU_HEIGHT = 196.dp

/**
 * Where each choice's lower right corner sits from the menu's, in [QuickKind] order: one to the sun's left, one up on
 * the diagonal, one above it, so they fan out like rays.
 */
private val ChoiceOffsets = listOf(
    (-86).dp to (-16).dp,
    (-64).dp to (-84).dp,
    (-4).dp to (-136).dp,
)
