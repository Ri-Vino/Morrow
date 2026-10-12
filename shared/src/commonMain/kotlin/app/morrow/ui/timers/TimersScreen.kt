package app.morrow.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.timers.Timer
import app.morrow.timers.TimerCoordinator
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

/**
 * The Timers tab, for now a button to start a 1-minute timer and a plain list of timers with Stop, enough to try the
 * engine on a phone. Without exact alarms, Start opens the permission screen rather than set a timer that can't ring.
 */
@Composable
fun TimersRoute(timers: TimerCoordinator, permissions: PermissionGate, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val all by timers.timers.collectAsStateWithLifecycle(initialValue = emptyList<Timer>())
    val ringing by timers.ringing.collectAsStateWithLifecycle()
    val waiting by timers.waiting.collectAsStateWithLifecycle()
    TimersScreen(
        model = timersModel(all, ringing.map { it.id }.toSet(), waiting.map { it.id }.toSet(), TimeZone.currentSystemDefault()),
        onStart = {
            scope.launch {
                if (timers.start(1.minutes) == null) permissions.request(timerPermission(permissions.asked))
            }
        },
        onStop = { id -> scope.launch { timers.stop(id) } },
        modifier = modifier,
    )
}

@Composable
fun TimersScreen(model: TimersModel, onStart: () -> Unit, onStop: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // Room to scroll the last timer clear of the tab bar.
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Timers", style = type.display.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        BasicText("Pip reads your note when one ends", style = type.body.copy(color = colors.muted))
        PrimaryButton("Start a 1 min timer", onClick = onStart)
        model.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(row.title, style = type.bodyStrong.copy(color = colors.text))
                    BasicText(row.detail, style = type.body.copy(color = colors.muted))
                }
                PillButton(
                    text = "Stop",
                    onClick = { onStop(row.id) },
                    background = colors.surface2,
                    content = colors.text,
                    modifier = Modifier.semantics { contentDescription = row.stopLabel },
                )
            }
        }
    }
}
