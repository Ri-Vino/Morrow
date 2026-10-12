package app.morrow.ui.timers

import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.fadeUpIn
import app.morrow.ui.components.rememberSceneSeconds
import app.morrow.ui.components.svgPath
import kotlin.math.PI
import kotlin.math.cos

/**
 * Timers that have ended, all stopped or given a minute together: Pip in a dusk sky above each timer's note and when it
 * ended. While he reads the notes out ([TimerDoneModel.reading]) he talks, with sound waves beside him and a badge
 * saying so. The notes scroll, so Stop and "+1 minute" stay on screen however large the text. [stopsByItself] says
 * whether the screen mentions the safety stop, which only Android has.
 */
@Composable
fun TimerDoneScreen(
    model: TimerDoneModel,
    onStop: () -> Unit,
    onAddMinute: () -> Unit,
    modifier: Modifier = Modifier,
    stopsByItself: Boolean = true,
) {
    MorrowTheme(ThemeKind.NIGHT) {
        TimerDoneContent(model, rememberSceneSeconds(still = Float.POSITIVE_INFINITY, fullRateFor = INTRO_LENGTH), onStop, onAddMinute, modifier, stopsByItself)
    }
}

@Composable
private fun TimerDoneContent(
    model: TimerDoneModel,
    seconds: State<Float>,
    onStop: () -> Unit,
    onAddMinute: () -> Unit,
    modifier: Modifier = Modifier,
    stopsByItself: Boolean = true,
) {
    val type = MorrowTheme.type
    val talking = model.reading != null
    val textIn = { delay: Float -> Ease.transform(((seconds.value - delay) / 0.8f).coerceIn(0f, 1f)) }
    Box(modifier.fillMaxSize().background(Illustration.duskSky)) {
        Canvas(Modifier.fillMaxSize()) { drawDusk(seconds.value, talking) }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(
                    "TIMER DONE",
                    style = type.label.copy(color = Illustration.onBadge, letterSpacing = 0.16.em),
                    modifier = Modifier.fadeUpIn { textIn(0.4f) }.semantics { heading() },
                )
                model.timers.forEachIndexed { index, timer ->
                    Column(
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BasicText(
                            timer.title,
                            style = type.display.copy(color = Illustration.starlight, textAlign = TextAlign.Center),
                            modifier = Modifier.widthIn(max = 330.dp).fadeUpIn { textIn(0.4f) },
                        )
                        BasicText(
                            timer.meta,
                            style = type.bodyLarge.copy(color = Illustration.sunriseGreeting, textAlign = TextAlign.Center),
                            modifier = Modifier.fadeUpIn { textIn(0.7f) },
                        )
                    }
                }
                model.reading?.let { ReadingBadge(it, seconds, Modifier.padding(top = 8.dp)) }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    text = "Stop",
                    onClick = onStop,
                    background = Illustration.sun,
                    content = Illustration.shades,
                    modifier = Modifier
                        .fillMaxWidth()
                        .dropShadow(CircleShape, Shadow(34.dp, Illustration.sun.copy(alpha = 0.28f), offset = DpOffset(0.dp, 14.dp)))
                        .semantics { contentDescription = model.stopLabel },
                    height = 64.dp,
                )
                PillButton(
                    text = "+1 minute",
                    onClick = onAddMinute,
                    background = Illustration.ground.copy(alpha = 0.5f),
                    content = Illustration.starlight,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = model.addMinuteLabel },
                    height = 56.dp,
                    border = Illustration.onBadge.copy(alpha = 0.3f),
                )
                if (stopsByItself) {
                    Box(Modifier.padding(top = 4.dp)) {
                        BasicText(
                            "Stops by itself after 10 minutes.",
                            style = type.body.copy(color = Illustration.starlightMuted, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}

/** "Pip is reading your note", beside a little level meter that moves with his voice. */
@Composable
private fun ReadingBadge(text: String, seconds: State<Float>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Illustration.ground.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp, 16.dp)) {
            scale(size.width / 22f, Offset.Zero) { drawLevels(seconds.value) }
        }
        BasicText(text, style = MorrowTheme.type.bodyStrong.copy(color = Illustration.onBadge))
    }
}

private const val WIDTH = 390f
private const val HEIGHT = 844f

/** Seconds until the text has faded in. */
private const val INTRO_LENGTH = 1.5f

/**
 * The dusk in the design's 390 × 844 units, scaled to the screen's width and kept on its bottom edge: rings of evening
 * light, Pip in a pulsing halo, and the hills. While [talking], Pip's mouth moves and sound waves leave it. With [t]
 * infinite it holds still. Decorative.
 */
private fun DrawScope.drawDusk(t: Float, talking: Boolean) {
    val unit = size.width / WIDTH
    val moving = t.isFinite()
    translate(top = size.height - HEIGHT * unit) {
        scale(unit, Offset.Zero) {
            Illustration.duskBands.forEachIndexed { i, band -> drawCircle(band, BandRadii[i], Offset(195f, 600f)) }
            translate(195f, 560f) {
                if (moving) {
                    val pulse = EaseOut.transform((t / 2.4f) % 1f)
                    drawCircle(Illustration.sunLight.copy(alpha = 0.6f * (1f - pulse)), 96f * (0.8f + 0.7f * pulse), Offset.Zero)
                }
                // Pip's body has a radius of 30 design units; here it's 64.
                scale(64f / 30f, Offset.Zero) {
                    drawPip(PipLook(PipMood.DELIGHTED, glow = false, talking = talking), if (moving) t else 0f)
                }
            }
            if (talking) translate(278f, 548f) { drawWaves(t) }
            drawPath(DuskHill, Illustration.duskHill)
            drawPath(DuskGround, Illustration.ground)
        }
    }
}

private val BandRadii = listOf(600f, 420f, 260f, 150f)

/** A sound wave, growing from its left edge, [delay] seconds behind the first. */
private class Wave(val path: Path, val pivot: Offset, val delay: Float)

private val Waves = listOf(
    Wave(svgPath("M0 -14 q10 14 0 28"), Offset(0f, 0f), delay = 0f),
    Wave(svgPath("M12 -26 q18 26 0 52"), Offset(12f, 0f), delay = 0.4f),
    Wave(svgPath("M24 -38 q26 38 0 76"), Offset(24f, 0f), delay = 0.8f),
)

/** Each wave grows from 70% to 125% over 1.6 s, fading in then out. Held whole when [t] is infinite. */
private fun DrawScope.drawWaves(t: Float) {
    for (wave in Waves) {
        var alpha = 1f
        var grow = 1f
        if (t.isFinite()) {
            val elapsed = t - wave.delay
            if (elapsed < 0f) continue
            val p = (elapsed / 1.6f) % 1f
            alpha = if (p < 0.3f) EaseOut.transform(p / 0.3f) else 1f - EaseOut.transform((p - 0.3f) / 0.7f)
            grow = 0.7f + 0.55f * EaseOut.transform(p)
        }
        scale(grow, wave.pivot) {
            drawPath(wave.path, Illustration.onBadge.copy(alpha = alpha), style = Stroke(5f, cap = StrokeCap.Round))
        }
    }
}

/** One bar of the badge's level meter, in its 22 × 16 units. */
private class Level(val x: Float, val top: Float, val height: Float, val delay: Float)

private val Levels = listOf(Level(0f, 2f, 12f, 0f), Level(6f, 0f, 16f, 0.2f), Level(12f, 3f, 11f, 0.45f), Level(18f, 5f, 8f, 0.1f))

/** Each bar rises from 35% to its full height and falls back, 0.9 s each way, out of step. Full when [t] is infinite. */
private fun DrawScope.drawLevels(t: Float) {
    for (level in Levels) {
        val rise = if (t.isFinite()) {
            val cycles = ((t - level.delay) / 0.9f).coerceAtLeast(0f)
            0.35f + 0.65f * (0.5f - 0.5f * cos(PI.toFloat() * cycles))
        } else {
            1f
        }
        val height = level.height * rise
        drawRoundRect(Illustration.onBadge, Offset(level.x, level.top + level.height - height), Size(3f, height), CornerRadius(1.5f))
    }
}

// Shapes from the design canvas. They run past the bottom edge so no sliver of sky shows below them.
private val DuskHill = svgPath("M0 640 C90 616 170 624 250 636 C310 644 350 628 390 632 L390 848 L0 848 Z")
private val DuskGround = svgPath("M0 690 C110 674 250 692 390 678 L390 848 L0 848 Z")

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TimerDoneReading() = MorrowTheme(ThemeKind.NIGHT) {
    TimerDoneContent(
        TimerDoneModel(
            timers = listOf(DoneTimer(9, "Take the pasta off the hob", "10 min timer · ended at 12:42", hasNote = true)),
            stopLabel = "Stop the timer",
            addMinuteLabel = "Ring again in a minute",
            reading = "Pip is reading your note",
        ),
        remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) },
        onStop = {},
        onAddMinute = {},
    )
}
