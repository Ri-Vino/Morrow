package app.morrow.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.components.Icons
import app.morrow.ui.components.largeText
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.SkyDial
import app.morrow.ui.components.pressable
import kotlinx.datetime.DayOfWeek

class EditActions(
    val onChange: (Alarm) -> Unit,
    val onBack: () -> Unit,
    val onDelete: () -> Unit,
    val onPreview: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun EditScreen(draft: Alarm, model: EditModel, actions: EditActions, modifier: Modifier = Modifier) {
    var addingGame by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        // Behind the sheet, the form is hidden from TalkBack as well as dimmed.
        EditForm(draft, model, actions, onAddGame = { addingGame = true }, Modifier.semantics { if (addingGame) hideFromAccessibility() })
        if (addingGame) {
            AddGameSheet(
                model.gameChoices,
                onPick = { type ->
                    addingGame = false
                    actions.onChange(draft.withGame(type))
                },
                onClose = { addingGame = false },
            )
        }
    }
}

@Composable
private fun EditForm(draft: Alarm, model: EditModel, actions: EditActions, onAddGame: () -> Unit, modifier: Modifier) {
    val colors = MorrowTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(model, actions)
            SkyDial(
                minuteOfDay = draft.hour * 60 + draft.minute,
                onMinuteChange = { actions.onChange(draft.at(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            TimeRow(model, onNudge = { actions.onChange(draft.nudged(it)) })
            BasicText(
                model.untilText,
                style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Section("Repeat") { DayChips(draft.days) { actions.onChange(draft.copy(days = it)) } }
            Section("Wake-up games", aside = "Played in this order") {
                GameList(draft, model.games, actions.onChange)
                EstimateRow(model, onAddGame)
            }
        }
        if (largeText()) {
            Column(Modifier.navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Save alarm", actions.onSave)
                SecondaryButton("Preview", actions.onPreview)
            }
        } else {
            Row(
                modifier = Modifier.navigationBarsPadding().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton("Preview", actions.onPreview, Modifier.weight(1f))
                PrimaryButton("Save alarm", actions.onSave, Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun Header(model: EditModel, actions: EditActions) {
    val colors = MorrowTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Back, "Back to alarms", actions.onBack)
        BasicText(
            model.title,
            style = MorrowTheme.type.title.copy(color = colors.text, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (model.canDelete) {
            RoundIconButton(Icons.Trash, "Delete alarm", actions.onDelete, tint = colors.muted)
        } else {
            Box(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun TimeRow(model: EditModel, onNudge: (Int) -> Unit) {
    val colors = MorrowTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        RoundIconButton(Icons.Minus, "Five minutes earlier", { onNudge(-DIAL_STEP_MINUTES) }, strokeWidth = 2.6f)
        Column(
            modifier = Modifier.width(176.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(model.daypart.uppercase(), style = MorrowTheme.type.label.copy(color = colors.hi))
            BasicText(model.time, style = MorrowTheme.type.displayLarge.copy(color = colors.text, fontFeatureSettings = "tnum"))
        }
        RoundIconButton(Icons.Plus, "Five minutes later", { onNudge(DIAL_STEP_MINUTES) }, strokeWidth = 2.6f)
    }
}

@Composable
private fun Section(title: String, aside: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            BasicText(
                title.uppercase(),
                style = MorrowTheme.type.label.copy(color = MorrowTheme.colors.muted),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (aside != null) BasicText(aside, style = MorrowTheme.type.caption.copy(color = MorrowTheme.colors.muted))
        }
        content()
    }
}

@Composable
private fun DayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val colors = MorrowTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            val name = day.name.lowercase().replaceFirstChar(Char::uppercase)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = name }
                    .toggleable(on, role = Role.Checkbox) { onChange(if (it) days + day else days - day) }
                    .background(if (on) colors.accent else colors.surface, CircleShape)
                    .then(if (on) Modifier.border(2.dp, colors.hi, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(name.take(1), style = MorrowTheme.type.button.copy(color = if (on) colors.onAccent else colors.muted))
            }
        }
    }
}

/**
 * The games in play order. Each can be moved by a long press and drag, or by TalkBack's "Move up" and "Move down".
 */
@Composable
private fun GameList(draft: Alarm, rows: List<GameRow>, onChange: (Alarm) -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(draft)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf<MissionType?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // Swaps with the neighbour once the row has been dragged over half its height.
    val drag = { dy: Float, rowHeight: Int ->
        dragging?.let { type ->
            offset += dy
            val index = latest.games.indexOfFirst { it.type == type }
            if (offset > rowHeight / 2f && index < latest.games.lastIndex) {
                change(latest.withGameMoved(type, 1))
                offset -= rowHeight
            } else if (offset < -rowHeight / 2f && index > 0) {
                change(latest.withGameMoved(type, -1))
                offset += rowHeight
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.line, shape)) {
        rows.forEach { row ->
            key(row.type) {
                val dragged = dragging == row.type
                GameRowView(
                    row = row,
                    onRounds = { change(latest.withRounds(row.type, it)) },
                    onRemove = { change(latest.withoutGame(row.type)) },
                    onMove = { change(latest.withGameMoved(row.type, it)) },
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) offset else 0f }
                        .then(if (dragged) Modifier.background(colors.surface2, shape) else Modifier)
                        .then(if (row.step > 1 && !dragged) Modifier.topDivider(colors.line) else Modifier)
                        .pointerInput(Unit) {
                            // Everything this reads is state, so the gesture stays current across moves.
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = row.type
                                    offset = 0f
                                },
                                onDrag = { pointer, amount ->
                                    pointer.consume()
                                    drag(amount.y, size.height)
                                },
                                onDragEnd = { dragging = null },
                                onDragCancel = { dragging = null },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun GameRowView(
    row: GameRow,
    onRounds: (Int) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .semantics(mergeDescendants = true) {
                customActions = listOfNotNull(
                    if (row.canMoveUp) CustomAccessibilityAction("Move up") { onMove(-1); true } else null,
                    if (row.canMoveDown) CustomAccessibilityAction("Move down") { onMove(1); true } else null,
                )
            }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(40.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
                MorrowIcon(Icons.forMission(row.type), colors.hi)
            }
            BasicText(
                row.step.toString(),
                style = type.label.copy(color = colors.onAccent, letterSpacing = type.caption.letterSpacing, textAlign = TextAlign.Center),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(18.dp)
                    .background(colors.accent, CircleShape)
                    .wrapContentHeight()
                    .clearAndSetSemantics {},
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(row.name, style = type.button.copy(color = colors.text))
            BasicText(
                row.roundsText,
                style = type.body.copy(color = colors.muted),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (row.counted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = row.name.lowercase()
                PlainIconButton(Icons.Minus, "Fewer $name rounds", { onRounds(row.rounds - 1) }, enabled = row.canFewer)
                BasicText(
                    row.rounds.toString(),
                    style = type.title.copy(color = colors.text, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                    modifier = Modifier.widthIn(min = 20.dp).clearAndSetSemantics {},
                )
                PlainIconButton(Icons.Plus, "More $name rounds", { onRounds(row.rounds + 1) }, enabled = row.canMore)
            }
        }
        if (row.removable) PlainIconButton(Icons.Close, "Remove ${row.name}", onRemove, tint = colors.muted)
    }
}

@Composable
private fun EstimateRow(model: EditModel, onAddGame: () -> Unit) {
    val colors = MorrowTheme.colors
    val estimate = @Composable { textModifier: Modifier ->
        BasicText(
            model.estimate.text,
            style = MorrowTheme.type.body.copy(color = if (model.estimate.tooLong) colors.warn else colors.muted),
            modifier = textModifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    val add = @Composable {
        if (model.canAddGame) {
            PillButton(
                "Add a game",
                onAddGame,
                background = colors.surface2,
                content = colors.text,
                icon = Icons.Plus,
                iconTint = colors.hi,
                padding = PaddingValues(start = 12.dp, end = 16.dp),
            )
        }
    }
    if (largeText()) {
        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            estimate(Modifier)
            add()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            estimate(Modifier.weight(1f))
            add()
        }
    }
}

/** Every game with a line about it. Games the alarm already has are dimmed, since each can be played once. */
@Composable
private fun AddGameSheet(choices: List<GameChoice>, onPick: (MissionType) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val listShape = RoundedCornerShape(24.dp)
    PlatformBackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Illustration.scrim).pressable(onClose).semantics { contentDescription = "Close" })
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(18.dp, sheetShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(colors.bg, sheetShape)
                .semantics { paneTitle = "Add a wake-up game" }
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(colors.line, CircleShape))
            BasicText("Add a wake-up game", style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
            Column(Modifier.fillMaxWidth().clip(listShape).background(colors.surface).border(1.dp, colors.line, listShape)) {
                choices.forEachIndexed { i, choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .then(if (i > 0) Modifier.topDivider(colors.line) else Modifier)
                            .then(
                                if (choice.added) {
                                    Modifier.semantics(mergeDescendants = true) {}
                                } else {
                                    Modifier.pressable({ onPick(choice.type) }, onClickLabel = "Add ${choice.name}")
                                },
                            )
                            .alpha(if (choice.added) 0.5f else 1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
                            MorrowIcon(Icons.forMission(choice.type), colors.hi)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(choice.name, style = type.button.copy(color = colors.text))
                            BasicText(choice.description, style = type.body.copy(color = colors.muted))
                        }
                        if (choice.added) {
                            BasicText("Added", style = type.bodyStrong.copy(color = colors.muted))
                        } else {
                            Box(Modifier.size(36.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                                MorrowIcon(Icons.Plus, colors.onAccent, size = 18.dp, strokeWidth = 2.6f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A hairline between rows of a list. */
private fun Modifier.topDivider(color: Color) = drawBehind { drawLine(color, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }

/** A 44 dp icon button with no background, dimmed when there's nothing it can do. */
@Composable
private fun PlainIconButton(
    icon: String,
    description: String,
    onClick: () -> Unit,
    tint: Color = MorrowTheme.colors.text,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable({ if (enabled) onClick() })
            .semantics { if (!enabled) disabled() }
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        MorrowIcon(icon, tint, size = 18.dp, strokeWidth = 2.6f, contentDescription = description)
    }
}

@Composable
private fun RoundIconButton(
    icon: String,
    description: String,
    onClick: () -> Unit,
    tint: Color = MorrowTheme.colors.text,
    strokeWidth: Float = 2.4f,
) {
    Box(
        modifier = Modifier.size(44.dp).pressable(onClick).background(MorrowTheme.colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        MorrowIcon(icon, tint, size = 22.dp, strokeWidth = strokeWidth, contentDescription = description)
    }
}
