package app.morrow.ui.morning

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.theme.mix
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.fadeUpIn
import app.morrow.ui.components.rememberSceneSeconds
import app.morrow.ui.components.svgPath
import app.morrow.ui.components.withAlpha
import kotlin.math.PI
import kotlin.math.cos

/** After the mission: a bright Pip, how the night went, and one small thing to look forward to. Always light. */
@Composable
fun MorningScreen(model: MorningModel, onStartDay: (intention: String) -> Unit) {
    MorrowTheme(ThemeKind.MORNING) {
        MorningContent(model, rememberSceneSeconds(still = Float.POSITIVE_INFINITY), onStartDay)
    }
}

@Composable
private fun MorningContent(model: MorningModel, seconds: State<Float>, onStartDay: (String) -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    var intention by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val textIn = { delay: Float -> Ease.transform(((seconds.value - delay) / 0.8f).coerceIn(0f, 1f)) }

    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val palette = MorningPalette(colors.surface, colors.surface2, mix(colors.surface2, colors.accent, 0.28f), colors.line, colors.bg)
            Canvas(Modifier.fillMaxWidth().aspectRatio(WIDTH / HEIGHT).clipToBounds()) {
                scale(size.width / WIDTH, Offset.Zero) { drawMorningSky(palette, seconds.value) }
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    model.hello,
                    style = type.display.copy(color = colors.text),
                    modifier = Modifier.fadeUpIn { textIn(0.4f) }.semantics { heading() },
                )
                BasicText(model.summary, style = type.bodyLarge.copy(color = colors.muted), modifier = Modifier.fadeUpIn { textIn(0.7f) })
            }
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fadeUpIn { textIn(0.7f) }
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(LABEL, style = type.bodyStrong.copy(color = colors.muted))
                val shape = RoundedCornerShape(16.dp)
                BasicTextField(
                    value = intention,
                    onValueChange = { intention = it.take(MAX_LENGTH) },
                    singleLine = true,
                    textStyle = type.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.hi),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(colors.bg, shape)
                        .border(2.dp, colors.line, shape)
                        .semantics { contentDescription = LABEL },
                    decorationBox = { field ->
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            if (intention.isEmpty()) BasicText("e.g. coffee by the window", style = type.bodyLarge.copy(color = colors.muted))
                            field()
                        }
                    },
                )
            }
        }
        Column(
            modifier = Modifier.navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton("Start my day", onClick = { onStartDay(intention.trim()) })
            BasicText(
                "Alarm off · sensors released · Pip sleeps until tomorrow",
                style = type.caption.copy(color = colors.muted, textAlign = TextAlign.Center),
            )
        }
    }
}

private const val LABEL = "One small thing to look forward to today"
private const val MAX_LENGTH = 60
private const val WIDTH = 390f
private const val HEIGHT = 370f

private class MorningPalette(val sky: Color, val glow: Color, val innerGlow: Color, val hill: Color, val ground: Color)

/** Pip pops up grinning in rings of morning light, rays turning and motes drifting up. With [t] infinite it holds still. Decorative. */
private fun DrawScope.drawMorningSky(palette: MorningPalette, t: Float) {
    val moving = t.isFinite()
    val centre = Offset(195f, 200f)
    drawRect(palette.sky, size = Size(WIDTH, HEIGHT))
    drawCircle(palette.glow, 200f, centre)
    drawCircle(palette.innerGlow, 146f, centre)

    val pop = Pop.transform(t.coerceIn(0f, 1f))
    translate(centre.x, centre.y) {
        withAlpha(pop.coerceIn(0f, 1f), Rect(-130f, -130f, 130f, 130f)) {
            scale(0.2f + 0.8f * pop, Offset.Zero) {
                rotate(if (moving) t * 6f else 0f, Offset.Zero) { // one turn a minute
                    repeat(16) { ray ->
                        rotate(ray * 22.5f, Offset.Zero) {
                            drawLine(Illustration.sun, Offset(0f, -92f), Offset(0f, -118f), strokeWidth = 8f, cap = StrokeCap.Round)
                        }
                    }
                }
                // Bobs up 5 units and back every 4 s.
                val bob = if (moving) -5f * (0.5f - 0.5f * cos(2f * PI.toFloat() * t / 4f)) else 0f
                translate(0f, bob) { drawGrinningPip() }
            }
        }
    }

    if (moving) Motes.forEach { drawMote(it, t) }
    drawPath(Hill, palette.hill)
    drawPath(Ground, palette.ground)
}

private fun DrawScope.drawGrinningPip() {
    val ink = Illustration.sunInk
    drawCircle(Illustration.sun, 74f, Offset.Zero)
    drawCircle(Illustration.sunLight.copy(alpha = 0.75f), 22f, Offset(-24f, -26f))
    drawPath(HappyEyes, ink, style = Stroke(5.5f, cap = StrokeCap.Round))
    drawCircle(Illustration.sunCheek.copy(alpha = 0.75f), 11f, Offset(-42f, 16f))
    drawCircle(Illustration.sunCheek.copy(alpha = 0.75f), 11f, Offset(42f, 16f))
    drawPath(Grin, ink)
    drawPath(Tongue, Illustration.sunCheek)
}

private class Mote(val x: Float, val y: Float, val radius: Float, val delay: Float, val color: Color)

private val Motes = listOf(
    Mote(40f, 330f, 4f, 0f, Illustration.sun), Mote(86f, 340f, 3f, 0.8f, Illustration.sunCheek),
    Mote(130f, 335f, 5f, 1.9f, Illustration.sunLight), Mote(170f, 345f, 3f, 2.7f, Illustration.sun),
    Mote(226f, 338f, 4f, 0.4f, Illustration.sunCheek), Mote(262f, 342f, 3f, 3.4f, Illustration.sun),
    Mote(300f, 332f, 5f, 1.4f, Illustration.sunLight), Mote(338f, 344f, 3f, 4.2f, Illustration.sunCheek),
    Mote(366f, 336f, 4f, 2.2f, Illustration.sun),
)

/** Rises 230 units over 6 s, glowing in and fading out, then starts again. */
private fun DrawScope.drawMote(mote: Mote, t: Float) {
    val elapsed = t - mote.delay
    if (elapsed < 0f) return
    val p = (elapsed / 6f) % 1f
    val alpha = if (p < 0.2f) 0.9f * EaseOut.transform(p / 0.2f) else 0.9f * (1f - EaseOut.transform((p - 0.2f) / 0.8f))
    drawCircle(mote.color.copy(alpha = alpha), mote.radius, Offset(mote.x, mote.y - 230f * EaseOut.transform(p)))
}

private val Pop = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

// Shapes from the design canvas.
private val HappyEyes = svgPath("M-36 -4 q11 -12 22 0M14 -4 q11 -12 22 0")
private val Grin = svgPath("M-18 18 q18 20 36 0Z")
private val Tongue = svgPath("M-8 26 q8 6 16 0Z")
private val Hill = svgPath("M0 318 C90 294 170 302 250 312 C310 320 350 302 390 308 L390 370 L0 370 Z")
private val Ground = svgPath("M0 348 C110 334 250 352 390 338 L390 372 L0 372 Z")

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MorningPreview() = MorrowTheme(ThemeKind.MORNING) {
    MorningContent(
        MorningModel("Good morning.", "You were up at 06:42, after about 7h 10m of sleep."),
        remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) },
        onStartDay = {},
    )
}
