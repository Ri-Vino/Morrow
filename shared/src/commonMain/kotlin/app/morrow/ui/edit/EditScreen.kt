package app.morrow.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.morrow.domain.Alarm
import app.morrow.domain.DEFAULT_ALARM_NAME
import app.morrow.domain.Difficulty
import app.morrow.domain.MAX_DESCRIPTION_LENGTH
import app.morrow.domain.MAX_NAME_LENGTH
import app.morrow.domain.MAX_SNOOZE_MINUTES
import app.morrow.domain.MissionType
import app.morrow.domain.RingSound
import app.morrow.domain.SnoozeChoices
import app.morrow.domain.SnoozeLimitChoices
import app.morrow.domain.UNLIMITED_SNOOZES
import app.morrow.domain.WakeSound
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.components.EntryReadout
import app.morrow.ui.components.Icons
import app.morrow.ui.components.Keypad
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.ReadoutPart
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.SegmentedControl
import app.morrow.ui.components.SkyDial
import app.morrow.ui.components.fadingWith
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import app.morrow.ui.components.rememberSheetEntrance
import app.morrow.ui.components.repeatingPressable
import app.morrow.ui.components.risingWith
import kotlinx.datetime.DayOfWeek

class EditActions(
    val onChange: (Alarm) -> Unit,
    val onBack: () -> Unit,
    val onDelete: () -> Unit,
    val onPreview: () -> Unit,
    val onSave: () -> Unit,
    /** Plays a choice from the Alarm sound sheet, null for the one chosen in Settings, until [onStopSound]. */
    val onHearSound: (RingSound?) -> Unit = {},
    val onStopSound: () -> Unit = {},
    /** Brings in a sound from the phone; null where that can't be done. */
    val onAddOwnSound: (() -> Unit)? = null,
    /** Takes one of the user's own sounds off the list. */
    val onRemoveOwnSound: (Long) -> Unit = {},
)

/** What the "Ring by the time in…" sheet offers: [choices] for the draft, and [find] for cities matching a search. */
class ZoneSheetData(val choices: List<ZoneChoice>, val find: suspend (String) -> List<ZoneChoice>)

@Composable
fun EditScreen(
    draft: Alarm,
    model: EditModel,
    actions: EditActions,
    zones: ZoneSheetData = ZoneSheetData(emptyList()) { emptyList() },
    modifier: Modifier = Modifier,
    /** Why the last sound brought in from the phone couldn't be used, shown in the Alarm sound sheet. */
    soundProblem: String? = null,
    /** The draft differs from the alarm as it was opened, so leaving asks before throwing the changes away. */
    unsaved: Boolean = false,
) {
    var addingGame by rememberSaveable { mutableStateOf(false) }
    var pickingZone by rememberSaveable { mutableStateOf(false) }
    var pickingSound by rememberSaveable { mutableStateOf(false) }
    var typingTime by rememberSaveable { mutableStateOf(false) }
    var typingSnooze by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (unsaved) confirmingDiscard = true else actions.onBack() }
    // Under the sheets' own back handlers, which close them first.
    PlatformBackHandler(enabled = unsaved) { confirmingDiscard = true }
    Box(modifier.fillMaxSize()) {
        // Behind a sheet, the form is hidden from TalkBack as well as dimmed.
        EditForm(
            draft, model, actions,
            onBack = leave,
            onAddGame = { addingGame = true },
            onPickZone = { pickingZone = true },
            onPickSound = { pickingSound = true },
            onTypeTime = { typingTime = true },
            onTypeSnooze = { typingSnooze = true },
            Modifier.semantics {
                if (addingGame || pickingZone || pickingSound || typingTime || typingSnooze || confirmingDiscard) hideFromAccessibility()
            },
        )
        if (confirmingDiscard) {
            DiscardSheet(
                isNew = !model.canDelete,
                onDiscard = {
                    confirmingDiscard = false
                    actions.onBack()
                },
                onKeep = { confirmingDiscard = false },
            )
        }
        if (typingTime) {
            TimeSheet(
                draft.hour,
                draft.minute,
                onSet = { minuteOfDay ->
                    typingTime = false
                    actions.onChange(draft.at(minuteOfDay))
                },
                onClose = { typingTime = false },
            )
        }
        if (typingSnooze) {
            SnoozeSheet(
                draft.snoozeMinutes,
                onSet = { minutes ->
                    typingSnooze = false
                    actions.onChange(draft.copy(snoozeMinutes = minutes))
                },
                onClose = { typingSnooze = false },
            )
        }
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
        if (pickingSound) {
            SoundSheet(
                model.soundChoices,
                problem = soundProblem,
                onAdd = actions.onAddOwnSound,
                onRemove = { choice -> (choice.sound as? RingSound.Own)?.let { actions.onRemoveOwnSound(it.id) } },
                onPick = { choice ->
                    actions.onChange(draft.copy(sound = choice.sound))
                    actions.onHearSound(choice.sound)
                },
                onClose = {
                    pickingSound = false
                    actions.onStopSound()
                },
            )
        }
        if (pickingZone) {
            ZoneSheet(
                zones,
                onPick = { choice ->
                    pickingZone = false
                    actions.onChange(draft.inZone(choice.timeZone, choice.city))
                },
                onClose = { pickingZone = false },
            )
        }
    }
}

/** Asked on the way out with changes not saved: throw them away, or go back to the form. */
@Composable
private fun DiscardSheet(isNew: Boolean, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val colors = MorrowTheme.colors
    Sheet(title = "Discard changes?", onClose = onKeep) {
        BasicText("Discard changes?", style = MorrowTheme.type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        BasicText(
            if (isNew) "This new alarm won’t be saved." else "The alarm stays as it was before you opened it.",
            style = MorrowTheme.type.body.copy(color = colors.muted),
        )
        PillButton("Discard changes", onDiscard, colors.warn, colors.onWarn, Modifier.fillMaxWidth(), height = 56.dp)
        SecondaryButton("Keep editing", onKeep)
    }
}

@Composable
private fun EditForm(
    draft: Alarm,
    model: EditModel,
    actions: EditActions,
    onBack: () -> Unit,
    onAddGame: () -> Unit,
    onPickZone: () -> Unit,
    onPickSound: () -> Unit,
    onTypeTime: () -> Unit,
    onTypeSnooze: () -> Unit,
    modifier: Modifier,
) {
    val colors = MorrowTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(model, onBack, actions.onDelete)
            SkyDial(
                minuteOfDay = draft.hour * 60 + draft.minute,
                onMinuteChange = { actions.onChange(draft.at(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            TimeRow(model, onNudge = { actions.onChange(draft.nudged(it)) }, onTypeTime = onTypeTime)
            BasicText(
                model.untilText,
                style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            ZoneRowView(model.zone, onPickZone)
            val focus = LocalFocusManager.current
            Section("Name") {
                TextEntry(
                    value = draft.label,
                    onValueChange = { actions.onChange(draft.copy(label = it.take(MAX_NAME_LENGTH))) },
                    description = "Alarm name",
                    placeholder = DEFAULT_ALARM_NAME,
                    singleLine = true,
                    imeAction = ImeAction.Next,
                    onAction = { focus.moveFocus(FocusDirection.Down) },
                )
            }
            Section("Description", aside = "Optional") {
                TextEntry(
                    value = draft.description.orEmpty(),
                    onValueChange = { actions.onChange(draft.copy(description = it.replace('\n', ' ').take(MAX_DESCRIPTION_LENGTH))) },
                    description = "Alarm description",
                    placeholder = "Gym bag is by the door",
                    singleLine = false,
                    imeAction = ImeAction.Done,
                    onAction = { focus.clearFocus() },
                )
                BasicText("Pip shows it when the alarm rings.", style = MorrowTheme.type.body.copy(color = colors.muted))
            }
            Section("Repeat") { DayChips(draft.days) { actions.onChange(draft.copy(days = it)) } }
            Section("Sound") { SoundRowView(model.sound, onPickSound) }
            Section("Wake-up games", aside = "Played in this order") {
                GameList(draft, model.games, actions.onChange)
                EstimateRow(model, onAddGame)
            }
            Section("Difficulty") {
                SegmentedControl(Difficulty.entries, model.difficulty, Difficulty::displayName, { actions.onChange(draft.copy(difficulty = it)) })
                BasicText(model.difficultyText, style = MorrowTheme.type.body.copy(color = colors.muted))
            }
            model.snooze?.let { snooze ->
                Section("Snooze") {
                    // Custom is selected whenever the length isn't one of the quick ones, and then names it.
                    val custom = snooze.minutes !in SnoozeChoices
                    SegmentedControl(
                        SnoozeChoices + CUSTOM_SNOOZE,
                        if (custom) CUSTOM_SNOOZE else snooze.minutes,
                        { if (it == CUSTOM_SNOOZE) (if (custom) "${snooze.minutes} min" else "Custom") else snoozeChoiceName(it) },
                        { if (it == CUSTOM_SNOOZE) onTypeSnooze() else actions.onChange(draft.copy(snoozeMinutes = it)) },
                        description = { if (it == CUSTOM_SNOOZE) "Custom snooze length" else snoozeChoiceName(it) },
                    )
                    if (snooze.minutes > 0) {
                        SegmentedControl(
                            SnoozeLimitChoices,
                            snooze.limit,
                            ::snoozeLimitName,
                            { actions.onChange(draft.copy(snoozeLimit = it)) },
                            description = { snoozeLimitDescription(it) },
                        )
                    }
                    BasicText(snooze.text, style = MorrowTheme.type.body.copy(color = colors.muted))
                }
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
private fun Header(model: EditModel, onBack: () -> Unit, onDelete: () -> Unit) {
    val colors = MorrowTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Back, "Back to alarms", onBack)
        BasicText(
            model.title,
            style = MorrowTheme.type.title.copy(color = colors.text, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (model.canDelete) {
            RoundIconButton(Icons.Trash, "Delete alarm", onDelete, tint = colors.muted)
        } else {
            Box(Modifier.size(44.dp))
        }
    }
}

/** The time between − and +, which repeat while held. Tapping the time opens the Set the time sheet. */
@Composable
private fun TimeRow(model: EditModel, onNudge: (Int) -> Unit, onTypeTime: () -> Unit) {
    val colors = MorrowTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        RepeatingIconButton(Icons.Minus, "Five minutes earlier") { onNudge(-DIAL_STEP_MINUTES) }
        Column(
            modifier = Modifier
                .width(176.dp)
                .clip(RoundedCornerShape(20.dp))
                .pressable(onTypeTime, onClickLabel = "Type the time")
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(model.daypart.uppercase(), style = MorrowTheme.type.label.copy(color = colors.hi))
            BasicText(model.time, style = MorrowTheme.type.displayLarge.copy(color = colors.text, fontFeatureSettings = "tnum"))
            // A dashed line under the time says it can be tapped.
            Box(
                Modifier.width(120.dp).height(2.dp).drawBehind {
                    val dash = 6.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(colors.line, Offset(x, size.height / 2), Offset(minOf(x + dash, size.width), size.height / 2), size.height)
                        x += dash * 2
                    }
                },
            )
        }
        RepeatingIconButton(Icons.Plus, "Five minutes later") { onNudge(DIAL_STEP_MINUTES) }
    }
}

@Composable
private fun RepeatingIconButton(icon: String, description: String, onPress: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .repeatingPressable(onPress, onClickLabel = description)
            .background(MorrowTheme.colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        MorrowIcon(icon, MorrowTheme.colors.text, size = 22.dp, strokeWidth = 2.6f, contentDescription = description)
    }
}

/** The time typed on the sheet's own keypad, hours then minutes; tapping either part types into just that one. */
@Composable
private fun TimeSheet(hour: Int, minute: Int, onSet: (Int) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    var entry by rememberSaveable(stateSaver = TimeEntry.Saver) { mutableStateOf(TimeEntry.at(hour * 60 + minute)) }
    val typed = entry.minuteOfDay
    Sheet("Set the time", onClose) {
        BasicText("Set the time", style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        EntryReadout(
            parts = listOf(
                ReadoutPart(entry.hours.padStart(2, '0'), 2 - entry.hours.length, "Hours"),
                ReadoutPart(entry.minutes.padStart(2, '0'), 2 - entry.minutes.length, "Minutes"),
            ),
            separator = ":",
            active = entry.part.ordinal,
            onPick = { entry = entry.pick(TimePart.entries[it]) },
        )
        BasicText(
            "24-hour clock. Tap the hours or minutes to change just those.",
            style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        Keypad(onDigit = { entry = entry.type(it) }, enabled = entry::canType) {
            Gap()
            Digit("0") { entry = entry.type(0) }
            Key("Delete digit", { entry = entry.delete() }) { MorrowIcon(Icons.DeleteDigit, colors.muted, size = 28.dp) }
        }
        PillButton(
            text = "Set time",
            onClick = { typed?.let(onSet) },
            background = if (typed != null) colors.accent else colors.surface2,
            content = if (typed != null) colors.onAccent else colors.muted,
            modifier = Modifier.fillMaxWidth().semantics { if (typed == null) disabled() },
            height = 56.dp,
        )
    }
}

/** A snooze length typed on the sheet's own keypad, from 1 to [MAX_SNOOZE_MINUTES] minutes. */
@Composable
private fun SnoozeSheet(current: Int, onSet: (Int) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    // Starts empty: the first digit typed is the first of the new length.
    var typed by rememberSaveable { mutableStateOf("") }
    val minutes = typedSnoozeMinutes(typed)
    Sheet("Snooze for", onClose) {
        BasicText("Snooze for", style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        EntryReadout(
            parts = listOf(
                ReadoutPart(typed.ifEmpty { "0" }, waiting = if (typed.isEmpty()) 1 else 0, name = "Minutes", unit = "min"),
            ),
        )
        BasicText(
            if (typed.isEmpty() && current > 0) "Now $current min. Type 1 to $MAX_SNOOZE_MINUTES minutes." else "Type 1 to $MAX_SNOOZE_MINUTES minutes.",
            style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        Keypad(onDigit = { typed = typeSnoozeMinutes(typed, it) }) {
            Gap()
            Digit("0") { typed = typeSnoozeMinutes(typed, 0) }
            Key("Delete digit", { typed = typed.dropLast(1) }) { MorrowIcon(Icons.DeleteDigit, colors.muted, size = 28.dp) }
        }
        PillButton(
            text = "Set snooze",
            onClick = { minutes?.let(onSet) },
            background = if (minutes != null) colors.accent else colors.surface2,
            content = if (minutes != null) colors.onAccent else colors.muted,
            modifier = Modifier.fillMaxWidth().semantics { if (minutes == null) disabled() },
            height = 56.dp,
        )
    }
}

private fun snoozeChoiceName(minutes: Int) = if (minutes == 0) "Off" else "$minutes min"

private fun snoozeLimitName(times: Int) = if (times == UNLIMITED_SNOOZES) "∞" else "$times×"

private fun snoozeLimitDescription(times: Int) = when (times) {
    UNLIMITED_SNOOZES -> "No limit"
    1 -> "Once"
    else -> "Up to $times times"
}

/** Stands for the Custom chip among the snooze lengths, which are never negative. */
private const val CUSTOM_SNOOZE = -1

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

/** A rounded text box on the form: one line, or wrapping onto up to three, with [placeholder] showing while it's empty. */
@Composable
private fun TextEntry(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    placeholder: String,
    singleLine: Boolean,
    imeAction: ImeAction,
    onAction: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        textStyle = type.bodyLarge.copy(color = colors.text),
        cursorBrush = SolidColor(colors.hi),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.surface, shape)
            .border(2.dp, colors.line, shape)
            .semantics { contentDescription = description },
        decorationBox = { field ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) BasicText(placeholder, style = type.bodyLarge.copy(color = colors.muted), maxLines = 1)
                field()
            }
        },
    )
}

@Composable
private fun DayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val colors = MorrowTheme.colors
    // Seven 44 dp days need 308 dp; on a narrower screen they share the width rather than spill, 39 dp each on a 320 dp
    // phone, still well above the 28 dp smallest target.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            val name = day.name.lowercase().replaceFirstChar(Char::uppercase)
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 44.dp)
                    .aspectRatio(1f)
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

/** The Time zone row: whose clock the alarm keeps. Opens the "Ring by the time in…" sheet. */
@Composable
private fun ZoneRowView(zone: ZoneRow, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .pressable(onClick, onClickLabel = "Choose the time zone")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(if (zone.zoned) colors.accent else colors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MorrowIcon(Icons.Globe, if (zone.zoned) colors.onAccent else colors.hi)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(zone.title, style = type.button.copy(color = colors.text))
            BasicText(zone.subtitle, style = type.body.copy(color = colors.muted))
        }
        MorrowIcon(Icons.Forward, colors.muted, strokeWidth = 2.2f)
    }
}

/** The phone's time and the saved cities on other clocks, then a search for any city. */
@Composable
private fun SoundRowView(sound: SoundRow, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .pressable(onClick, onClickLabel = "Choose the alarm sound")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(if (sound.own) colors.accent else colors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MorrowIcon(Icons.Music, if (sound.own) colors.onAccent else colors.hi)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(sound.title, style = type.button.copy(color = colors.text))
            BasicText(sound.subtitle, style = type.body.copy(color = colors.muted))
        }
        MorrowIcon(Icons.Forward, colors.muted, strokeWidth = 2.2f)
    }
}

/** Default and every sound; picking one plays it, and the sound stops when the sheet closes. */
@Composable
private fun SoundSheet(
    choices: List<SoundChoice>,
    problem: String?,
    onAdd: (() -> Unit)?,
    onRemove: (SoundChoice) -> Unit,
    onPick: (SoundChoice) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Sheet("Alarm sound", onClose) {
        BasicText("Alarm sound", style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        BasicText("Tap one to hear it.", style = type.body.copy(color = colors.muted))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupedByRun(choices) { it.group }.forEach { (group, inGroup) ->
                if (group != null) {
                    BasicText(
                        group.uppercase(),
                        style = type.caption.copy(color = colors.muted),
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp).semantics { heading() },
                    )
                }
                SoundCard(inGroup, onPick, onRemove)
                // Their own sounds come first; if there are none yet, the way to add one does.
                if (onAdd != null && (group == OWN_SOUNDS || (group == null && choices.none { it.removable }))) {
                    SecondaryButton("Add a sound from your phone", onAdd)
                    problem?.let {
                        BasicText(
                            it,
                            style = type.body.copy(color = colors.warn),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
        PrimaryButton("Done", onClose)
    }
}

/** One group of the Alarm sound sheet's choices, in a card. */
@Composable
private fun SoundCard(choices: List<SoundChoice>, onPick: (SoundChoice) -> Unit, onRemove: (SoundChoice) -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.line, shape)) {
        choices.forEachIndexed { i, choice ->
            Row(
                modifier = Modifier.fillMaxWidth().then(if (i > 0) Modifier.topDivider(colors.line) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .selectable(choice.selected, role = Role.RadioButton) { onPick(choice) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val ring = if (choice.selected) colors.hi else colors.muted
                    Box(Modifier.size(24.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                        if (choice.selected) Box(Modifier.size(12.dp).background(colors.hi, CircleShape))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(choice.name, style = type.button.copy(color = colors.text))
                        BasicText(choice.subtitle, style = type.body.copy(color = colors.muted))
                    }
                }
                if (choice.removable) {
                    Box(
                        modifier = Modifier.size(48.dp).pressable({ onRemove(choice) }, onClickLabel = "Remove ${choice.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorrowIcon(Icons.Close, colors.muted, size = 18.dp, strokeWidth = 2.2f, contentDescription = "Remove ${choice.name}")
                    }
                }
            }
        }
    }
}

/** [items] cut into runs that share a [key], in order. */
private fun <T, K> groupedByRun(items: List<T>, key: (T) -> K): List<Pair<K, List<T>>> =
    items.fold(mutableListOf<Pair<K, MutableList<T>>>()) { runs, item ->
        val k = key(item)
        if (runs.isNotEmpty() && runs.last().first == k) runs.last().second += item else runs += k to mutableListOf(item)
        runs
    }

@Composable
private fun ZoneSheet(zones: ZoneSheetData, onPick: (ZoneChoice) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    var finding by rememberSaveable { mutableStateOf(false) }
    Sheet("Ring by the time in", onClose = { if (finding) finding = false else onClose() }, onScrimClick = onClose) {
        if (finding) {
            CitySearch(zones.find, onPick, onBack = { finding = false })
        } else {
            BasicText("Ring by the time in…", style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
            BasicText(
                "For a meeting abroad. The alarm keeps to that city’s clock, summer time included, and shows you the time here.",
                style = type.body.copy(color = colors.muted),
            )
            ChoiceList(zones.choices, onPick, Modifier.selectableGroup())
            SecondaryButton("Find another city", { finding = true })
        }
    }
}

/** A city search over the bundled list. Picking a city sets the alarm's zone; it isn't added to the World tab. */
@Composable
private fun CitySearch(find: suspend (String) -> List<ZoneChoice>, onPick: (ZoneChoice) -> Unit, onBack: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    var query by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<ZoneChoice>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) { found = if (query.isBlank()) null else find(query) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(Icons.Back, "Back to time zones", onBack)
        BasicText("Find another city", style = type.title.copy(color = colors.text), modifier = Modifier.weight(1f).semantics { heading() })
    }
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = query,
        onValueChange = { query = it.take(60) },
        singleLine = true,
        textStyle = type.bodyLarge.copy(color = colors.text),
        cursorBrush = SolidColor(colors.hi),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRequester(focus)
            .background(colors.surface, shape)
            .border(2.dp, colors.line, shape)
            .semantics { contentDescription = "City name" },
        decorationBox = { field ->
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) BasicText("Search for a city", style = type.bodyLarge.copy(color = colors.muted), maxLines = 1)
                field()
            }
        },
    )
    val results = found
    when {
        results == null -> Unit
        results.isEmpty() -> BasicText(
            "No city by that name",
            style = type.body.copy(color = colors.muted),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        else -> ChoiceList(results, onPick, radio = false)
    }
}

/** Zone choices in a card: radio buttons in the sheet, or plain rows for search results. */
@Composable
private fun ChoiceList(choices: List<ZoneChoice>, onPick: (ZoneChoice) -> Unit, modifier: Modifier = Modifier, radio: Boolean = true) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.line, shape)) {
        choices.forEachIndexed { i, choice ->
            val pick = { onPick(choice) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .then(if (i > 0) Modifier.topDivider(colors.line) else Modifier)
                    .then(
                        if (radio) {
                            Modifier.selectable(choice.selected, role = Role.RadioButton, onClick = pick)
                        } else {
                            Modifier.pressable(pick, onClickLabel = "Ring by ${choice.name} time")
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (radio) {
                    val ring = if (choice.selected) colors.hi else colors.muted
                    Box(Modifier.size(24.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                        if (choice.selected) Box(Modifier.size(12.dp).background(colors.hi, CircleShape))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(choice.name, style = type.button.copy(color = colors.text))
                    BasicText(choice.subtitle, style = type.body.copy(color = colors.muted))
                }
                BasicText(choice.time, style = type.bodyStrong.copy(color = colors.muted, fontFeatureSettings = "tnum"))
            }
        }
    }
}

/**
 * A sheet over a dimmed screen, named [title] for TalkBack. Back calls [onClose]; tapping the dimmed screen calls
 * [onScrimClick].
 */
@Composable
private fun Sheet(title: String, onClose: () -> Unit, onScrimClick: () -> Unit = onClose, content: @Composable ColumnScope.() -> Unit) {
    val colors = MorrowTheme.colors
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    PlatformBackHandler(onBack = onClose)
    val entrance = rememberSheetEntrance()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().fadingWith(entrance).background(Illustration.scrim).pressable(onScrimClick).semantics { contentDescription = "Close" },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .risingWith(entrance)
                .fillMaxWidth()
                .shadow(18.dp, sheetShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(colors.bg, sheetShape)
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(colors.line, CircleShape))
            content()
        }
    }
}

/** Every game with a line about it. Games the alarm already has are dimmed, since each can be played once. */
@Composable
private fun AddGameSheet(choices: List<GameChoice>, onPick: (MissionType) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val listShape = RoundedCornerShape(24.dp)
    Sheet("Add a wake-up game", onClose) {
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
