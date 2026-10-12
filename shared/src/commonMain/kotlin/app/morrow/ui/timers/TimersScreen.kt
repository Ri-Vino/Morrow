package app.morrow.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.timers.Timer
import app.morrow.timers.TimerCoordinator
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Timers tab over [timers]. Without exact alarms, Start and Resume open the permission screen rather than set a
 * timer that can't ring. What has been typed into the setter survives rotation. [ticks] is the time, then each second
 * while a timer counts down; the clock's by default, and only while the tab is visible.
 */
@Composable
fun TimersRoute(
    timers: TimerCoordinator,
    permissions: PermissionGate,
    modifier: Modifier = Modifier,
    ticks: Flow<Instant>? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val scope = rememberCoroutineScope()
    val stored by timers.timers.collectAsStateWithLifecycle<List<Timer>?>(initialValue = null)
    val ringing by timers.ringing.collectAsStateWithLifecycle()
    val waiting by timers.waiting.collectAsStateWithLifecycle()
    val now by remember(stored, ticks) {
        val all = stored.orEmpty()
        ticks ?: secondTicks { at -> all.any { timer -> timer.endsAt?.let { it > at } == true } }
    }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    var draft by rememberSaveable(stateSaver = TimerDraft.Saver) { mutableStateOf(TimerDraft()) }
    var starting by remember { mutableStateOf(false) }

    // Until the timers are read, the setter would flash up as though there were none.
    val all = stored ?: return
    fun askForExactAlarms() = permissions.request(timerPermission(permissions.asked))

    TimersScreen(
        model = timersModel(all, ringing.mapTo(HashSet()) { it.id }, waiting.mapTo(HashSet()) { it.id }, now, zone, draft),
        actions = TimersActions(
            onDigit = { draft = draft.copy(entry = draft.entry.type(it)) },
            onDeleteDigit = { draft = draft.copy(entry = draft.entry.deleteLast()) },
            onQuick = { draft = draft.copy(entry = it) },
            onNote = { draft = draft.copy(note = it) },
            onStart = {
                val entry = draft.entry
                val note = draft.note
                if (entry.canStart && !starting) {
                    starting = true
                    scope.launch {
                        try {
                            if (timers.start(entry.duration, note) == null) askForExactAlarms() else draft = TimerDraft()
                        } finally {
                            starting = false
                        }
                    }
                }
            },
            onCancel = { draft = TimerDraft() },
            onNewTimer = { draft = draft.copy(adding = true) },
            onAddMinute = { id -> scope.launch { timers.addMinute(id) } },
            onPause = { id -> scope.launch { timers.pause(id) } },
            onResume = { id -> scope.launch { if (!timers.resume(id)) askForExactAlarms() } },
            onRemove = { id -> scope.launch { timers.stop(id) } },
        ),
        modifier = modifier,
    )
}

class TimersActions(
    /** A digit or "00" from the keypad. */
    val onDigit: (String) -> Unit,
    val onDeleteDigit: () -> Unit,
    val onQuick: (TimerEntry) -> Unit,
    val onNote: (String) -> Unit,
    val onStart: () -> Unit,
    val onCancel: () -> Unit,
    val onNewTimer: () -> Unit,
    val onAddMinute: (id: Long) -> Unit,
    val onPause: (id: Long) -> Unit,
    val onResume: (id: Long) -> Unit,
    val onRemove: (id: Long) -> Unit,
)

/** The Timers tab: the setter when there is nothing running or "New timer" opened it, then a card for each timer. */
@Composable
fun TimersScreen(model: TimersModel, actions: TimersActions, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                // Room to scroll the last card clear of the New timer button and the tab bar.
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(model.sub)
            model.setter?.let { TimerSetter(it, actions) }
            model.cards.forEach { card -> key(card.id) { TimerCardView(card, actions) } }
        }
        if (model.showNewTimer) {
            PillButton(
                text = "New timer",
                onClick = actions.onNewTimer,
                background = colors.accent,
                content = colors.onAccent,
                icon = Icons.Plus,
                height = 56.dp,
                padding = PaddingValues(start = 16.dp, end = 24.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 104.dp)
                    .shadow(14.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow),
            )
        }
    }
}

@Composable
private fun Header(sub: String) {
    val colors = MorrowTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            sub.uppercase(),
            style = MorrowTheme.type.label.copy(color = colors.muted),
            modifier = Modifier.semantics {
                contentDescription = sub
                liveRegion = LiveRegionMode.Polite
            },
        )
        BasicText("Timers", style = MorrowTheme.type.display.copy(color = colors.text), modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun TimerCardView(card: TimerCard, actions: TimersActions) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape),
    ) {
        TimerSkyStrip(card.sky, Modifier.fillMaxWidth().aspectRatio(STRIP_WIDTH / STRIP_HEIGHT))
        val padding = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)
        if (largeText()) {
            // The time and its buttons won't sit side by side at big text sizes, so the buttons go underneath.
            Column(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardText(card)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardButtons(card, actions)
                }
            }
        } else {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CardText(card, Modifier.weight(1f))
                CardButtons(card, actions)
            }
        }
    }
}

/** The time left in the display face, or that it is up, with the note and total beneath. Read as one thing. */
@Composable
private fun CardText(card: TimerCard, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val time = when {
        !card.showsClock -> type.title
        card.longClock -> type.display.copy(fontSize = type.display.fontSize * LONG_CLOCK_SCALE, lineHeight = type.display.lineHeight * LONG_CLOCK_SCALE)
        else -> type.display
    }
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            card.time,
            style = time.copy(color = if (card.state == TimerState.PAUSED) colors.muted else colors.text, fontFeatureSettings = "tnum"),
        )
        BasicText(card.detail, style = type.body.copy(color = colors.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CardButtons(card: TimerCard, actions: TimersActions) {
    val colors = MorrowTheme.colors
    PillButton(
        text = "+1 min",
        onClick = { actions.onAddMinute(card.id) },
        background = colors.surface2,
        content = colors.text,
        modifier = Modifier.semantics { contentDescription = card.addLabel },
        padding = PaddingValues(horizontal = 12.dp),
    )
    if (card.canToggle) {
        val running = card.state == TimerState.RUNNING
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable({ if (running) actions.onPause(card.id) else actions.onResume(card.id) })
                .background(colors.accent, CircleShape)
                .semantics { contentDescription = card.toggleLabel },
            contentAlignment = Alignment.Center,
        ) {
            MorrowIcon(if (running) PAUSE else PLAY, colors.onAccent, size = 22.dp, strokeWidth = 2.6f)
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable({ actions.onRemove(card.id) })
            .semantics { contentDescription = card.removeLabel },
        contentAlignment = Alignment.Center,
    ) {
        MorrowIcon(Icons.Close, colors.muted, size = 20.dp, strokeWidth = 2.4f)
    }
}

private const val PAUSE = "M8.5 5v14M15.5 5v14"
private const val PLAY = "M8 5.5v13l10.5-6.5z"

/** A clock with hours, "1:02:03", is set at 32 where the others are 40. */
private const val LONG_CLOCK_SCALE = 0.8f
