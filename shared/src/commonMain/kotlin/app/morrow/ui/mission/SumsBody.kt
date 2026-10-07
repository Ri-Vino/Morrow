package app.morrow.ui.mission

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.missions.SumsMission
import app.morrow.missions.SumsState
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.pressable

@Composable
internal fun SumsBody(state: SumsState, mission: SumsMission) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(
            state.sum.question,
            style = type.displayLarge.copy(color = colors.text, textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(24.dp)
                .semantics { contentDescription = "What is ${spoken(state.sum.question)}?" },
        )

        val shake = remember { Animatable(1f) }
        LaunchedEffect(state.wrong) {
            if (state.wrong > 0) {
                shake.snapTo(0f)
                shake.animateTo(1f, tween(450, easing = LinearEasing))
            }
        }
        val missed = state.wrong > 0 && state.answer.isEmpty()
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer { translationX = shakeOffset(shake.value).dp.toPx() }
                .background(colors.bg, shape)
                .border(2.dp, if (missed) Illustration.mistake else colors.line, shape)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = when {
                        state.answer.isNotEmpty() -> "Your answer: ${state.answer}"
                        missed -> "Not quite. Try again."
                        else -> "No answer yet"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(state.answer.ifEmpty { "?" }, style = type.display.copy(color = colors.text, fontFeatureSettings = "tnum"))
        }

        Keypad(onDigit = mission::type, onDelete = mission::delete, onCheck = mission::check)
    }
}

@Composable
private fun Keypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, onCheck: () -> Unit) {
    val colors = MorrowTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> DigitKey(digit, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(onDelete, colors.surface, "Delete digit") { MorrowIcon(Icons.DeleteDigit, colors.muted, size = 28.dp) }
            DigitKey(0, onDigit)
            Key(onCheck, colors.accent, "Check answer") { MorrowIcon(Icons.Check, colors.onAccent, size = 28.dp, strokeWidth = 3f) }
        }
    }
}

@Composable
private fun RowScope.DigitKey(digit: Int, onDigit: (Int) -> Unit) {
    Key({ onDigit(digit) }, MorrowTheme.colors.surface, label = null) {
        BasicText(digit.toString(), style = MorrowTheme.type.title.copy(color = MorrowTheme.colors.text))
    }
}

@Composable
private fun RowScope.Key(onClick: () -> Unit, background: Color, label: String?, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .pressable(onClick)
            .background(background, RoundedCornerShape(20.dp))
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** How far, in dp, a field shaking "no" has moved at [t] from 0 to 1. */
internal fun shakeOffset(t: Float): Float = keyframes(t, 0f, -12f, 10f, -6f, 3f, 0f)

/** Linear steps through evenly spaced [values] as [t] goes from 0 to 1. */
internal fun keyframes(t: Float, vararg values: Float): Float {
    val position = t.coerceIn(0f, 1f) * (values.size - 1)
    val i = position.toInt().coerceAtMost(values.size - 2)
    val f = position - i
    return values[i] + (values[i + 1] - values[i]) * f
}

/** Reads the maths signs as words, so screen readers don't skip them. */
private fun spoken(question: String) = question.replace("−", "minus").replace("×", "times").replace("+", "plus")
