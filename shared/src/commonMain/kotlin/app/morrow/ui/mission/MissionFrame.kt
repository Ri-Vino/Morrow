package app.morrow.ui.mission

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.mix
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.svgPath
import app.morrow.ui.components.withAlpha

/**
 * Every mission's screen: a strip of sky where Pip rises with [progress], the mission's header with a chip for each
 * game in the sequence, if there's more than one, and Pip's line, then [body].
 */
@Composable
internal fun MissionFrame(
    model: MissionModel,
    progress: Float,
    chips: List<StepChip> = emptyList(),
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        Box {
            RisingSky(progress)
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("WAKE-UP MISSION", style = type.label.copy(color = Illustration.sunriseGreeting))
                    BasicText(model.title, style = type.title.copy(color = Illustration.glint), modifier = Modifier.semantics { heading() })
                    if (chips.isNotEmpty()) StepChips(chips)
                }
                BasicText(
                    model.count,
                    style = type.bodyStrong.copy(color = Illustration.onBadge),
                    modifier = Modifier
                        .background(Illustration.ground.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                model.pipLine,
                style = type.title.copy(color = colors.text, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = body,
            )
        }
    }
}

@Composable
private fun StepChips(chips: List<StepChip>) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            val background = when (chip.state) {
                ChipState.DONE -> Illustration.sun
                ChipState.NOW -> Illustration.onBadge.copy(alpha = 0.2f)
                ChipState.NEXT -> Illustration.ground.copy(alpha = 0.5f)
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(background, CircleShape)
                    .then(if (chip.state == ChipState.NOW) Modifier.border(2.dp, Illustration.onBadge, CircleShape) else Modifier)
                    .semantics { contentDescription = chip.label },
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (chip.state == ChipState.DONE) Illustration.sunInk else Illustration.onBadge
                MorrowIcon(Icons.forMission(chip.type), tint, size = 16.dp, strokeWidth = 2.2f)
            }
        }
    }
}

private const val WIDTH = 390f
private const val HEIGHT = 200f

/** Night turning to dawn as the mission goes on: the moon sets and Pip climbs over the hill. Decorative. */
@Composable
private fun RisingSky(progress: Float) {
    val ground = MorrowTheme.colors.bg
    val rise by animateFloatAsState(progress, tween(1000, easing = Overshoot))
    val warmth by animateFloatAsState(progress, tween(1000))
    val awake by animateFloatAsState(if (progress >= 0.5f) 1f else 0f, tween(400))
    Canvas(Modifier.fillMaxWidth().aspectRatio(WIDTH / HEIGHT).clipToBounds()) {
        scale(size.width / WIDTH, Offset.Zero) {
            val sky = mix(Illustration.dawnNight, Illustration.dawnRose, warmth)
            drawRect(sky, size = Size(WIDTH, HEIGHT))
            val night = 1f - warmth
            Stars.forEach { (at, radius) -> drawCircle(Illustration.starlight.copy(alpha = night), radius, at) }
            if (night > 0f) {
                translate(322f, 110f + 70f * warmth) {
                    withAlpha(night, Rect(-20f, -20f, 20f, 20f)) {
                        scale(17f / 30f, Offset.Zero) { drawPip(Moon, seconds = 0f) }
                    }
                }
            }
            translate(195f, 196f - 104f * rise) { drawWakingPip(glow = 0.15f + 0.35f * warmth, awake = awake) }
            drawPath(Hill, mix(sky, Illustration.ground, 0.45f))
            drawPath(Ground, ground)
        }
    }
}

private val Overshoot = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)

private val Moon = PipLook(PipMood.ASLEEP, moon = true, glow = false)

private val Stars = listOf(
    Offset(40f, 90f) to 1.4f, Offset(96f, 120f) to 1.1f, Offset(300f, 96f) to 1.5f,
    Offset(350f, 130f) to 1.1f, Offset(250f, 70f) to 1.2f, Offset(140f, 76f) to 1.2f,
)

/** Pip in the mission sky: eyes shut until halfway, then open. */
private fun DrawScope.drawWakingPip(glow: Float, awake: Float) {
    val ink = Illustration.sunInk
    drawCircle(Illustration.sunLight.copy(alpha = glow), 54f, Offset.Zero)
    drawCircle(Illustration.sun, 34f, Offset.Zero)
    drawCircle(Illustration.sunLight.copy(alpha = 0.7f), 10f, Offset(-10f, -11f))
    drawPath(SleepyEyes, ink.copy(alpha = 1f - awake), style = Stroke(3f, cap = StrokeCap.Round))
    drawCircle(ink.copy(alpha = awake), 3.6f, Offset(-11f, -3f))
    drawCircle(ink.copy(alpha = awake), 3.6f, Offset(11f, -3f))
    drawCircle(Illustration.sunCheek.copy(alpha = 0.75f), 5f, Offset(-20f, 8f))
    drawCircle(Illustration.sunCheek.copy(alpha = 0.75f), 5f, Offset(20f, 8f))
    drawPath(Smile, ink, style = Stroke(2.6f, cap = StrokeCap.Round))
}

// Shapes from the design canvas.
private val SleepyEyes = svgPath("M-17 -3 h11M6 -3 h11")
private val Smile = svgPath("M-6 11 q6 5 12 0")
private val Hill = svgPath("M0 166 C80 146 160 156 240 162 C300 166 350 152 390 158 L390 200 L0 200 Z")
private val Ground = svgPath("M0 188 C110 178 250 192 390 182 L390 202 L0 202 Z")
