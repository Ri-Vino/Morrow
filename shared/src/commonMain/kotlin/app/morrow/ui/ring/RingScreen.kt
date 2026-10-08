package app.morrow.ui.ring

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.fadeUpIn
import app.morrow.ui.components.pressable
import app.morrow.ui.components.rememberSceneSeconds
import app.morrow.ui.components.svgPath
import app.morrow.ui.components.withAlpha
import kotlin.math.floor

/** Extra controls when the screen is a preview from Home or Edit rather than a real alarm. */
class RingPreview(val soundOn: Boolean, val onToggleSound: () -> Unit, val onExit: () -> Unit)

/** The ringing alarm: a sunrise with Pip waking up, the alarm's time and label, and the way into the mission. */
@Composable
fun RingScreen(model: RingModel, onStartMission: () -> Unit, modifier: Modifier = Modifier, preview: RingPreview? = null) {
    MorrowTheme(ThemeKind.NIGHT) {
        RingContent(model, rememberSceneSeconds(still = SUNRISE_DONE), onStartMission, modifier, preview)
    }
}

@Composable
private fun RingContent(
    model: RingModel,
    seconds: State<Float>,
    onStartMission: () -> Unit,
    modifier: Modifier = Modifier,
    preview: RingPreview? = null,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val frame by remember(seconds) { derivedStateOf { sunriseFrame(seconds.value) } }
    Box(modifier.fillMaxSize().background(Illustration.sunriseBands.first().first)) {
        Canvas(Modifier.fillMaxSize()) { drawSunrise(frame, seconds.value) }

        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val textIn = { index: Int -> frame.textIn[index] }
            BasicText(
                "Rise and shine",
                style = type.button.copy(color = Illustration.sunriseGreeting),
                modifier = Modifier.fadeUpIn { textIn(0) },
            )
            BasicText(
                model.time,
                style = type.hero.copy(color = colors.text, fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 8.dp).fadeUpIn { textIn(0) },
            )
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fadeUpIn { textIn(1) }
                    .background(Illustration.ground.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorrowIcon(model.missionIcon, Illustration.onBadge, size = 15.dp, strokeWidth = 2.2f)
                BasicText(model.label, style = type.bodyStrong.copy(color = Illustration.onBadge))
            }
            BasicText(
                "“Morning! I’m up — your turn.”",
                style = type.title.copy(color = Illustration.glint, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 24.dp).fadeUpIn { textIn(2) },
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = model.startLabel,
                onClick = onStartMission,
                background = colors.accent,
                content = colors.onAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .dropShadow(CircleShape, Shadow(34.dp, colors.accent.copy(alpha = 0.28f), offset = DpOffset(0.dp, 14.dp))),
                height = 64.dp,
                icon = Icons.ArrowRight,
                iconAfter = true,
            )
            BasicText(
                "Pip keeps singing until you’re done. It stops by itself after 10 minutes, just in case.",
                style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            )
            if (preview != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkButton("Exit preview", preview.onExit)
                    LinkButton(if (preview.soundOn) "Stop sound" else "Play sound", preview.onToggleSound)
                }
            }
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).pressable(onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = MorrowTheme.type.bodyStrong.copy(color = MorrowTheme.colors.muted, textDecoration = TextDecoration.Underline),
        )
    }
}

private const val WIDTH = 390f
private const val HEIGHT = 844f

/**
 * The sunrise in the design's 390 × 844 units, scaled to the screen's width and kept on its bottom edge.
 * [t] drives the looping parts (twinkles, halo, birds, notes); they stop when the sunrise is held finished.
 */
private fun DrawScope.drawSunrise(frame: SunriseFrame, t: Float) {
    val unit = size.width / WIDTH
    val looping = t.isFinite()
    drawRect(frame.bands.first())
    translate(top = size.height - HEIGHT * unit) {
        scale(unit, Offset.Zero) {
            frame.bands.drop(1).forEachIndexed { i, band -> drawCircle(band, BandRadii[i], Offset(195f, 630f)) }

            val moonAlpha = 1f - frame.moonSet
            if (moonAlpha > 0f) {
                translate(332f + 34f * frame.moonSet, 330f + 150f * frame.moonSet) {
                    withAlpha(moonAlpha, Rect(-30f, -30f, 30f, 30f)) {
                        scale(0.8f, Offset.Zero) { drawPip(SettingMoon, seconds = 0f) }
                    }
                }
            }

            for (star in Stars) {
                val twinkle = if (looping) 0.25f + 0.75f * EaseInOut.transform(pingPong((t - star.delay) / 1.6f)) else 1f
                drawCircle(Illustration.starlight.copy(alpha = frame.starAlpha * twinkle), star.radius, Offset(star.x, star.y))
            }

            if (looping) Birds.forEach { drawBird(it, t) }

            translate(195f, 600f + 190f * (1f - frame.rise)) { drawRisingPip(frame, t, looping) }

            drawPath(HillRise, frame.hill)
            drawPath(HillFront, Illustration.ground)
        }
    }
}

private val BandRadii = listOf(520f, 340f, 200f)

private val SettingMoon = PipLook(PipMood.ASLEEP, moon = true, glow = false)

private fun DrawScope.drawRisingPip(frame: SunriseFrame, t: Float, looping: Boolean) {
    if (looping) {
        val pulse = EaseOut.transform((t / 3.2f) % 1f)
        drawCircle(Illustration.sunLight.copy(alpha = 0.6f * (1f - pulse)), 120f * (0.8f + 0.7f * pulse), Offset.Zero)
    }
    if (frame.raysAlpha > 0f) {
        rotate(if (looping) t * 360f / 50f else 0f, Offset.Zero) {
            repeat(12) { ray ->
                rotate(ray * 30f, Offset.Zero) {
                    drawLine(
                        Illustration.sunLight.copy(alpha = frame.raysAlpha),
                        Offset(0f, -88f),
                        Offset(0f, -110f),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }

    val ink = Illustration.sunInk
    drawCircle(Illustration.sun, 72f, Offset.Zero)
    drawCircle(Illustration.sunLight.copy(alpha = 0.7f), 22f, Offset(-22f, -24f))
    drawPath(SleepyEyes, ink.copy(alpha = frame.eyesShut), style = Stroke(5f, cap = StrokeCap.Round))
    if (frame.eyesOpen > 0f) {
        drawCircle(ink.copy(alpha = frame.eyesOpen), 6.5f, Offset(-22f, -6f))
        drawCircle(ink.copy(alpha = frame.eyesOpen), 6.5f, Offset(22f, -6f))
        drawCircle(Illustration.glint.copy(alpha = frame.eyesOpen), 2f, Offset(-20f, -8.5f))
        drawCircle(Illustration.glint.copy(alpha = frame.eyesOpen), 2f, Offset(24f, -8.5f))
    }
    drawCircle(Illustration.sunCheek.copy(alpha = 0.7f), 10f, Offset(-40f, 14f))
    drawCircle(Illustration.sunCheek.copy(alpha = 0.7f), 10f, Offset(40f, 14f))
    drawOval(ink.copy(alpha = frame.yawn), topLeft = Offset(-9f, 12f), size = Size(18f, 24f))
    drawPath(BigSmile, ink.copy(alpha = frame.smile), style = Stroke(5f, cap = StrokeCap.Round))

    if (looping) Notes.forEach { drawNote(it, t) }
}

private class Star(val x: Float, val y: Float, val radius: Float, val delay: Float)

private val Stars = listOf(
    Star(40f, 70f, 1.6f, 0f), Star(120f, 40f, 1.2f, 0.8f), Star(300f, 60f, 1.6f, 1.6f), Star(360f, 130f, 1.2f, 0.4f),
    Star(70f, 300f, 1.3f, 2.1f), Star(330f, 330f, 1.3f, 1.2f), Star(24f, 200f, 1.1f, 2.6f),
)

private class Bird(val y: Float, val delay: Float, val crossing: Float, val flapDelay: Float, val wings: String, val stroke: Float) {
    val path = svgPath(wings)
    val middle = path.getBounds().center
}

private val Birds = listOf(
    Bird(150f, delay = 3f, crossing = 16f, flapDelay = 0f, wings = "M0 0 q6 -7 12 0 q6 -7 12 0", stroke = 2.4f),
    Bird(182f, delay = 4.2f, crossing = 19f, flapDelay = 0.2f, wings = "M0 0 q5 -6 10 0 q5 -6 10 0", stroke = 2f),
)

/** A bird crossing the sky left to right, flapping its wings every 0.45 s. */
private fun DrawScope.drawBird(bird: Bird, t: Float) {
    val flying = t - bird.delay
    if (flying < 0f) return
    val x = -60f + 520f * ((flying / bird.crossing) % 1f)
    val flapping = t - bird.flapDelay
    val flap = if (flapping < 0f) 1f else 1f - 0.75f * EaseInOut.transform(pingPong(flapping / 0.45f))
    translate(x, bird.y) {
        scale(1f, flap, bird.middle) {
            drawPath(bird.path, Illustration.birds, style = Stroke(bird.stroke, cap = StrokeCap.Round))
        }
    }
}

/** A note from Pip's song, around Pip's centre, with one or two heads. */
private class Note(val x: Float, val y: Float, val delay: Float, val stem: String, val heads: List<Offset>) {
    val path = svgPath(stem)
}

private val Notes = listOf(
    Note(70f, -70f, delay = 4f, stem = "M0 0 v-18 l12 -4 v15", heads = listOf(Offset(-3f, 1f), Offset(9f, -3f))),
    Note(-86f, -50f, delay = 5.2f, stem = "M0 0 v-16", heads = listOf(Offset(-3f, 1f))),
    Note(40f, -96f, delay = 6.3f, stem = "M0 0 v-16", heads = listOf(Offset(-3f, 1f))),
)

/** Floats up and to the right, tilting, over 3.2 s, then starts again. */
private fun DrawScope.drawNote(note: Note, t: Float) {
    val elapsed = t - note.delay
    if (elapsed < 0f) return
    val p = (elapsed / 3.2f) % 1f
    val drift = EaseOut.transform(p)
    val alpha = if (p < 0.2f) EaseOut.transform(p / 0.2f) else 1f - EaseOut.transform((p - 0.2f) / 0.8f)
    val color = Illustration.onBadge.copy(alpha = alpha)
    translate(note.x + 26f * drift, note.y - 90f * drift) {
        rotate(14f * drift, Offset.Zero) {
            drawPath(note.path, color, style = Stroke(2.6f, cap = StrokeCap.Round))
            note.heads.forEach { drawCircle(color, 4f, it) }
        }
    }
}

/** Goes 0 → 1 → 0 as [cycles] counts up by 2. */
private fun pingPong(cycles: Float): Float {
    val whole = floor(cycles)
    val part = cycles - whole
    return if (whole.toInt() % 2 == 0) part else 1f - part
}

// Shapes from the design canvas.
private val SleepyEyes = svgPath("M-34 -6 q11 10 22 0M12 -6 q11 10 22 0")
private val BigSmile = svgPath("M-14 20 q14 14 28 0")
private val HillRise = svgPath("M0 640 C90 610 170 618 250 632 C310 642 350 626 390 630 L390 848 L0 848 Z")
private val HillFront = svgPath("M0 690 C110 672 250 690 390 676 L390 848 L0 848 Z")

private val PreviewModel = ringModel(Alarm(1, 6, 30, "Work days", emptySet(), MissionType.SUMS, enabled = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RingAsleep() = RingPreviewAt(0f)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RingYawning() = RingPreviewAt(3f)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RingRisen() = RingPreviewAt(SUNRISE_DONE)

@Composable
private fun RingPreviewAt(seconds: Float) = MorrowTheme(ThemeKind.NIGHT) {
    RingContent(PreviewModel, remember { mutableFloatStateOf(seconds) }, onStartMission = {}, preview = RingPreview(false, {}, {}))
}
