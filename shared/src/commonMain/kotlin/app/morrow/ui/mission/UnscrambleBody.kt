package app.morrow.ui.mission

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morrow.missions.UnscrambleMission
import app.morrow.missions.UnscrambleState
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.pressable

@Composable
internal fun UnscrambleBody(state: UnscrambleState, mission: UnscrambleMission) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shake = remember { Animatable(1f) }
    LaunchedEffect(state.wrong) {
        if (state.wrong > 0) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(450, easing = LinearEasing))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(20.dp)
                .clearAndSetSemantics {
                    contentDescription = unscrambleSoFar(state)
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.words.take(state.placed).forEach { word ->
                Box(
                    Modifier.heightIn(min = 40.dp).background(colors.accent, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(word, style = type.button.copy(color = colors.onAccent))
                }
            }
            state.words.drop(state.placed).forEach { word -> Slot(width = 28.dp + 9.dp * word.length) }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.order.forEachIndexed { index, place ->
                if (place >= state.placed) {
                    key(index) { WordChip(state.words[place], wrong = index == state.wrongIndex, shake = shake.value) { mission.pick(index) } }
                }
            }
        }

        BasicText(
            unscrambleHint(state),
            style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun WordChip(word: String, wrong: Boolean, shake: Float, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .graphicsLayer { if (wrong) translationX = shakeOffset(shake).dp.toPx() }
            .pressable(onClick)
            .background(colors.surface, shape)
            .border(2.dp, if (wrong) Illustration.mistake else colors.line, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(word, style = MorrowTheme.type.button.copy(color = colors.text))
    }
}

/** An empty place for a word, as wide as the word will be. */
@Composable
private fun Slot(width: Dp) {
    val line = MorrowTheme.colors.line
    Box(
        Modifier
            .size(width, 40.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val dash = floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                drawRoundRect(
                    line,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(dash)),
                )
            },
    )
}
