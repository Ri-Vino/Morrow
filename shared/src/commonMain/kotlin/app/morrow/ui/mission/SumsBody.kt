package app.morrow.ui.mission

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import app.morrow.ui.components.Keypad
import app.morrow.ui.components.MorrowIcon

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
                .heightIn(min = 64.dp)
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

        Keypad(onDigit = mission::type) {
            Key("Delete digit", mission::delete) { MorrowIcon(Icons.DeleteDigit, colors.muted, size = 28.dp) }
            Digit("0") { mission.type(0) }
            Key("Check answer", mission::check, colors.accent) { MorrowIcon(Icons.Check, colors.onAccent, size = 28.dp, strokeWidth = 3f) }
        }
    }
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
