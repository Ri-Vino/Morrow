package app.morrow.ui.firstrun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.theme.mix
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.rememberSceneSeconds
import app.morrow.ui.components.svgPath
import kotlin.math.PI
import kotlin.math.cos

private class Promise(val title: String, val detail: String, val icon: String)

private val Promises = listOf(
    Promise("Rings on time, every time", "Your phone’s own alarm clock does the waking.", Icons.Clock),
    Promise("A sunrise, not a siren", "The sky warms up and the sound grows slowly.", Icons.Sunrise),
    Promise("A small mission to get you up", "Sums, sentences, breaths, shakes or a kettle hunt.", Icons.Check),
)

@Composable
fun WelcomeScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            WelcomeArt()
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("WELCOME TO MORROW", style = MorrowTheme.type.label.copy(color = colors.hi))
                BasicText(
                    "Hi, I’m Pip.",
                    style = MorrowTheme.type.display.copy(color = colors.text),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    "I’ll wake you with a slow sunrise and a small mission, so you actually get up.",
                    style = MorrowTheme.type.bodyLarge.copy(color = colors.muted),
                )
            }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Promises.forEach { promise ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                            MorrowIcon(promise.icon, colors.hi)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(promise.title, style = MorrowTheme.type.bodyStrong.copy(color = colors.text))
                            BasicText(promise.detail, style = MorrowTheme.type.body.copy(color = colors.muted))
                        }
                    }
                }
            }
        }
        PrimaryButton(
            "Let’s get set up",
            onContinue,
            Modifier.navigationBarsPadding().padding(24.dp),
            icon = Icons.ArrowRight,
            iconAfter = true,
        )
    }
}

/** Pip grinning in front of two rings of light, from the design's 390 × 340 artwork. Decorative. */
@Composable
private fun WelcomeArt() {
    val colors = MorrowTheme.colors
    val seconds = rememberSceneSeconds()
    Canvas(Modifier.fillMaxWidth().aspectRatio(390f / 340f).clipToBounds()) {
        val t = seconds.value
        scale(size.width / 390f, Offset.Zero) {
            drawRect(colors.surface, size = Size(390f, 340f))
            drawCircle(colors.surface2, 190f, Offset(195f, 190f))
            drawCircle(mix(colors.surface2, colors.accent, 0.28f), 138f, Offset(195f, 190f))
            // Pip bobs gently, 5 units every 4 s.
            val bob = -2.5f * (1f - cos(2f * PI.toFloat() * t / 4f))
            translate(195f, 186f + bob) {
                scale(2.4f, Offset.Zero) { drawPip(PipLook(PipMood.DELIGHTED, rays = true), t) }
            }
            Sparkles.forEach { (x, y, r, delay) ->
                val twinkle = 0.25f + 0.75f * (0.5f - 0.5f * cos(2f * PI.toFloat() * (t - delay) / 3.2f))
                drawCircle(colors.accent.copy(alpha = twinkle), r, Offset(x, y))
            }
            drawPath(WelcomeHill, colors.line)
            drawPath(WelcomeGround, colors.bg)
        }
    }
}

private data class Sparkle(val x: Float, val y: Float, val radius: Float, val delay: Float)

private val Sparkles = listOf(Sparkle(62f, 96f, 4f, 0f), Sparkle(332f, 70f, 3f, 1.1f), Sparkle(320f, 250f, 4f, 2f), Sparkle(48f, 236f, 3f, 0.6f))
private val WelcomeHill = svgPath("M0 290 C90 266 170 274 250 284 C310 292 350 274 390 280 L390 340 L0 340 Z")
private val WelcomeGround = svgPath("M0 320 C110 306 250 324 390 310 L390 344 L0 344 Z")
