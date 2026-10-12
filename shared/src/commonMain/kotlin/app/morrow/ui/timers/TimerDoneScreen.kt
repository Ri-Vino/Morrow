package app.morrow.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.components.PillButton

/**
 * Timers that have ended, all stopped or given a minute together. For now a plain dusk with the notes; Pip, the sky and
 * his voice come later, over the same [model].
 */
@Composable
fun TimerDoneScreen(model: TimerDoneModel, onStop: () -> Unit, onAddMinute: () -> Unit, modifier: Modifier = Modifier) {
    MorrowTheme(ThemeKind.NIGHT) {
        val type = MorrowTheme.type
        Column(modifier.fillMaxSize().background(Illustration.duskSky).statusBarsPadding().navigationBarsPadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(
                    "TIMER DONE",
                    style = type.label.copy(color = Illustration.onBadge),
                    modifier = Modifier.semantics { heading() },
                )
                model.timers.forEachIndexed { index, timer ->
                    Column(
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BasicText(timer.title, style = type.display.copy(color = Illustration.starlight, textAlign = TextAlign.Center))
                        BasicText(timer.meta, style = type.bodyLarge.copy(color = Illustration.sunriseGreeting, textAlign = TextAlign.Center))
                    }
                }
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
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = model.stopLabel },
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
