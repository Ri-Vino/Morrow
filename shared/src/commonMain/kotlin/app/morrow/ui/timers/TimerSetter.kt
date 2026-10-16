package app.morrow.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.BottomSheet
import app.morrow.ui.components.EntryReadout
import app.morrow.ui.components.Icons
import app.morrow.ui.components.Keypad
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.ReadoutPart
import app.morrow.ui.components.largeText

/**
 * The setter: the wind-up dial with the length in its middle, quick chips, the note for Pip to read out, and Start
 * (with Cancel when it was opened over running timers). Tapping the length calls [onType].
 */
@Composable
internal fun TimerSetter(setter: SetterModel, actions: TimersActions, onType: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(28.dp)
    val focus = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TimerDial(
            entry = setter.entry,
            onWind = { focus.clearFocus(); actions.onEntry(it) },
            onType = { focus.clearFocus(); onType() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        QuickChips(setter.entry, onPick = { focus.clearFocus(); actions.onEntry(it) })
        NoteField(setter.note, actions.onNote, focus, Modifier.padding(horizontal = 20.dp))
        StartRow(setter, actions, Modifier.padding(horizontal = 20.dp))
    }
}

/** The chips run edge to edge on the card and scroll sideways; with large text they wrap instead, so none is hidden. The one matching the dial is lit. */
@Composable
private fun QuickChips(entry: TimerEntry, onPick: (TimerEntry) -> Unit) {
    val colors = MorrowTheme.colors
    val chip = @Composable { quick: QuickTimer ->
        val picked = quick.entry.duration == entry.duration
        PillButton(
            text = quick.name,
            onClick = { onPick(quick.entry) },
            background = if (picked) colors.accent else colors.bg,
            content = if (picked) colors.onAccent else colors.text,
            border = if (picked) colors.hi else colors.line,
            padding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.semantics { selected = picked },
        )
    }
    if (largeText()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { QuickTimers.forEach { chip(it) } }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { QuickTimers.forEach { chip(it) } }
    }
}

/**
 * The length typed on the sheet's own keypad, filling in from the right: 1, 5, 0, 0 is 15 minutes. It opens on the
 * dial's length, which the first key typed replaces.
 */
@Composable
internal fun TimerTypeSheet(entry: TimerEntry, onSet: (TimerEntry) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    var digits by rememberSaveable { mutableStateOf(entry.digits) }
    var fresh by rememberSaveable { mutableStateOf(true) }
    val typed = TimerEntry(digits)
    val key = { digit: Int ->
        digits = (if (fresh) TimerEntry() else TimerEntry(digits)).typed(digit).digits
        fresh = false
    }
    BottomSheet("Set the length", onClose) {
        BasicText("Set the length", style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        EntryReadout(
            parts = listOf(
                ReadoutPart(typed.hoursText, typed.waiting.coerceIn(0, 2), "Hours", "h"),
                ReadoutPart(typed.minutesText, (typed.waiting - 2).coerceIn(0, 2), "Minutes", "m"),
                ReadoutPart(typed.secondsText, (typed.waiting - 4).coerceIn(0, 2), "Seconds", "s"),
            ),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = typed.label
                liveRegion = LiveRegionMode.Polite
            },
        )
        BasicText(
            "Digits fill in from the right: 1, 5, 0, 0 is 15 minutes.",
            style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        Keypad(onDigit = key) {
            Digit("00") {
                key(0)
                key(0)
            }
            Digit("0") { key(0) }
            Key("Delete digit", {
                digits = TimerEntry(digits).deleted().digits
                fresh = false
            }) { MorrowIcon(Icons.DeleteDigit, colors.muted, size = 28.dp) }
        }
        val ready = typed.canStart
        PillButton(
            text = "Set length",
            onClick = { if (ready) onSet(typed) },
            background = if (ready) colors.accent else colors.surface2,
            content = if (ready) colors.onAccent else colors.muted,
            modifier = Modifier.fillMaxWidth().semantics { if (!ready) disabled() },
            height = 56.dp,
        )
    }
}

@Composable
private fun NoteField(note: String, onNote: (String) -> Unit, focus: FocusManager, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(NOTE_LABEL, style = type.bodyStrong.copy(color = colors.muted))
        BasicTextField(
            value = note,
            onValueChange = { onNote(it.take(MAX_NOTE_LENGTH)) },
            singleLine = true,
            textStyle = type.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(colors.hi),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(colors.bg, shape)
                .border(2.dp, colors.line, shape)
                .semantics { contentDescription = NOTE_LABEL },
            decorationBox = { field ->
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (note.isEmpty()) {
                        BasicText(NOTE_HINT, style = type.bodyLarge.copy(color = colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
            },
        )
    }
}

@Composable
private fun StartRow(setter: SetterModel, actions: TimersActions, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val ready = setter.entry.canStart
    val start = @Composable { buttonModifier: Modifier ->
        PillButton(
            text = "Start timer",
            onClick = { if (ready) actions.onStart() },
            background = if (ready) colors.accent else colors.surface2,
            content = if (ready) colors.onAccent else colors.muted,
            modifier = buttonModifier.semantics { if (!ready) disabled() },
            height = 56.dp,
        )
    }
    val cancel = @Composable { buttonModifier: Modifier ->
        PillButton("Cancel", actions.onCancel, colors.bg, colors.text, buttonModifier, height = 56.dp, border = colors.line)
    }
    if (largeText()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            start(Modifier.fillMaxWidth())
            if (setter.canCancel) cancel(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (setter.canCancel) cancel(Modifier.weight(1f))
            start(Modifier.weight(2f))
        }
    }
}

internal const val NOTE_LABEL = "Note for Pip to read out (optional)"
private const val NOTE_HINT = "e.g. Take the pasta off the hob"
