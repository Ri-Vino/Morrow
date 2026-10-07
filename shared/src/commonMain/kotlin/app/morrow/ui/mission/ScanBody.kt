package app.morrow.ui.mission

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.missions.ScanState
import app.morrow.platform.CodeScanner
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.svgPath

@Composable
internal fun ScanBody(
    state: ScanState,
    scanner: CodeScanner,
    cameraAllowed: Boolean,
    onCode: (String) -> Unit,
    onSwitchToSums: () -> Unit,
) {
    val colors = MorrowTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (cameraAllowed) {
            ScanWindow(scanner, found = state.found, wrong = state.wrong, onCode = onCode)
        } else {
            CameraOff()
        }
        BasicText(
            scanText(state, cameraAllowed),
            style = MorrowTheme.type.bodyLarge.copy(color = colors.text, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (!cameraAllowed) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Allow camera", scanner::requestCamera)
                SecondaryButton("Switch to sums instead", onSwitchToSums)
            }
        }
    }
}

/**
 * The camera picture in a rounded window with corner brackets: a sweeping line while looking, coral and a shake
 * after a [wrong] code, a tick once [found]. The camera stops as soon as the code is found.
 */
@Composable
internal fun ScanWindow(scanner: CodeScanner, found: Boolean, wrong: Int, onCode: (String) -> Unit) {
    val colors = MorrowTheme.colors
    val shake = remember { Animatable(1f) }
    LaunchedEffect(wrong) {
        if (wrong > 0) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(450, easing = LinearEasing))
        }
    }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(found) { if (found) pop.animateTo(1f, tween(600, easing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f))) }
    val sweep by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse),
    )
    val showWrong = wrong > 0 && !found
    val bracket = when {
        found -> Illustration.sunLight
        showWrong -> Illustration.mistake
        else -> colors.accent
    }

    Box(
        Modifier
            .size(250.dp)
            .graphicsLayer { translationX = shakeOffset(shake.value).dp.toPx() }
            .clip(RoundedCornerShape(30.dp))
            .background(Illustration.viewfinder),
    ) {
        if (!found) scanner.Viewfinder(onCode, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 250f, Offset.Zero) {
                drawPath(Brackets, bracket, style = Stroke(5f, cap = StrokeCap.Round))
                if (!found) {
                    val y = 26f + 196f * sweep
                    drawRect(colors.accent.copy(alpha = 0.25f), Offset(26f, y - 5f), Size(198f, 13f))
                    drawRect(colors.accent, Offset(26f, y), Size(198f, 3f))
                } else {
                    val p = pop.value
                    scale(0.2f + 0.8f * p, Offset(125f, 125f)) {
                        drawCircle(colors.accent.copy(alpha = p.coerceIn(0f, 1f)), 46f, Offset(125f, 125f))
                        drawPath(Tick, colors.onAccent.copy(alpha = p.coerceIn(0f, 1f)), style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        if (showWrong) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Illustration.viewfinder.copy(alpha = 0.88f), RoundedCornerShape(20.dp)),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorrowIcon(Icons.Close, Illustration.mistake, size = 16.dp, strokeWidth = 3f)
                BasicText("Not your kettle code", style = MorrowTheme.type.bodyStrong.copy(color = Illustration.onBadge))
            }
        }
    }
}

@Composable
private fun CameraOff() {
    val colors = MorrowTheme.colors
    Column(
        modifier = Modifier.size(250.dp).background(Illustration.viewfinder, RoundedCornerShape(30.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        MorrowIcon(Icons.CameraOff, colors.muted, size = 48.dp, strokeWidth = 1.8f)
        BasicText("Camera is off", style = MorrowTheme.type.button.copy(color = colors.text))
        BasicText(
            "Morrow only uses it during this mission, and never saves a picture.",
            style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
        )
    }
}

// Shapes from the design canvas, in a 250 × 250 window.
private val Brackets = svgPath(
    "M22 58 v-24 a12 12 0 0 1 12 -12 h24M192 22 h24 a12 12 0 0 1 12 12 v24" +
        "M228 192 v24 a12 12 0 0 1 -12 12 h-24M58 228 h-24 a12 12 0 0 1 -12 -12 v-24",
)
private val Tick = svgPath("M104 126 l14 14 l28 -30")
