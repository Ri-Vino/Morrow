package app.morrow.ui.mission

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.missions.ShakeState
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.mix
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.svgPath
import kotlinx.coroutines.delay

/**
 * The shake game. Sums are offered in its place straight away on a phone with no motion sensor, and after
 * [OFFER_SUMS_AFTER_MILLIS] on one with, for a morning when shaking is hard; waiting that long keeps it a wake-up.
 */
@Composable
internal fun ShakeBody(state: ShakeState, sensorAvailable: Boolean, onSwitchToSums: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val filled by animateFloatAsState(state.progress, tween(350, easing = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)))
    var offerSums by remember { mutableStateOf(!sensorAvailable) }
    LaunchedEffect(Unit) {
        delay(OFFER_SUMS_AFTER_MILLIS)
        offerSums = true
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShakingPhone(state)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText("Shakes", style = type.body.copy(color = colors.muted))
                BasicText("${state.shakes} / ${state.goal}", style = type.title.copy(color = colors.hi))
            }
            Box(Modifier.fillMaxWidth().height(12.dp).background(colors.surface, RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxWidth(filled.coerceIn(0f, 1f)).height(12.dp).background(colors.accent, RoundedCornerShape(6.dp)))
            }
        }
        BasicText(
            when {
                !sensorAvailable -> "This phone has no motion sensor, so Pip can’t count shakes."
                offerSums -> "Can’t shake right now? Sums wake you up just as well."
                else -> "Give your phone a good shake. The motion sensor switches off the moment you finish."
            },
            style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (offerSums) SecondaryButton("Switch to sums instead", onSwitchToSums)
    }
}

internal const val OFFER_SUMS_AFTER_MILLIS = 60_000L

/** A phone with Pip on its screen, wobbling with each shake and warming up as the count rises. Decorative. */
@Composable
private fun ShakingPhone(state: ShakeState) {
    val colors = MorrowTheme.colors
    val wobble = remember { Animatable(1f) }
    LaunchedEffect(state.shakes) {
        if (state.shakes > 0) {
            wobble.snapTo(0f)
            wobble.animateTo(1f, tween(500, easing = LinearEasing))
        }
    }
    val awake = state.progress >= 0.5f
    Canvas(Modifier.size(200.dp, 210.dp)) {
        scale(size.width / 200f, Offset.Zero) {
            drawPath(Waves, Illustration.sunLight.copy(alpha = 0.7f), style = Stroke(4f, cap = StrokeCap.Round))
            translate(100f, 105f) {
                rotate(keyframes(wobble.value, 0f, -14f, 12f, -7f, 0f), Offset.Zero) {
                    drawRoundRect(colors.surface, Offset(-50f, -98f), Size(100f, 196f), CornerRadius(22f))
                    drawRoundRect(colors.line, Offset(-50f, -98f), Size(100f, 196f), CornerRadius(22f), style = Stroke(3f))
                    drawRoundRect(
                        mix(Illustration.dawnNight, Illustration.dawnRose, state.progress),
                        Offset(-40f, -80f),
                        Size(80f, 160f),
                        CornerRadius(10f),
                    )
                    drawRoundRect(colors.line, Offset(-12f, -91f), Size(24f, 5f), CornerRadius(2.5f))
                    val ink = Illustration.sunInk
                    drawCircle(Illustration.sun, 22f, Offset.Zero)
                    if (awake) {
                        drawCircle(ink, 2.6f, Offset(-7f, -2f))
                        drawCircle(ink, 2.6f, Offset(7f, -2f))
                    } else {
                        drawPath(SleepyEyes, ink, style = Stroke(2.4f, cap = StrokeCap.Round))
                    }
                    drawPath(Smile, ink, style = Stroke(2f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

// Shapes from the design canvas.
private val Waves = svgPath("M28 80 q-14 25 0 50M12 70 q-20 35 0 70M172 80 q14 25 0 50M188 70 q20 35 0 70")
private val SleepyEyes = svgPath("M-11 -2 h7M4 -2 h7")
private val Smile = svgPath("M-4 7 q4 3 8 0")
