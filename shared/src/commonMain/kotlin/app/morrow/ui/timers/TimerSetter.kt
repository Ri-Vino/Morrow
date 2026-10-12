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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.Icons
import app.morrow.ui.components.Keypad
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.largeText

/**
 * The setter: the digits shifting in from the right, quick chips, the Sums keypad with 00, the note for Pip to read
 * out, and Start (with Cancel when it was opened over running timers).
 */
@Composable
internal fun TimerSetter(setter: SetterModel, actions: TimersActions, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(28.dp)
    val focus = LocalFocusManager.current
    // Typing on the keypad puts the keyboard away, so it doesn't cover the keys.
    val typed = { digits: String -> focus.clearFocus(); actions.onDigit(digits) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EntryDigits(setter.entry, Modifier.padding(horizontal = 20.dp))
        QuickChips(onPick = { focus.clearFocus(); actions.onQuick(it) })
        Keypad(
            onDigit = { typed(it.toString()) },
            modifier = Modifier.padding(horizontal = 20.dp),
            keyBackground = colors.surface2,
            minKeyHeight = 48.dp,
        ) {
            Digit("00") { typed("00") }
            Digit("0") { typed("0") }
            Key("Delete digit", { focus.clearFocus(); actions.onDeleteDigit() }) {
                MorrowIcon(Icons.DeleteDigit, colors.muted, size = 26.dp)
            }
        }
        NoteField(setter.note, actions.onNote, focus, Modifier.padding(horizontal = 20.dp))
        StartRow(setter, actions, Modifier.padding(horizontal = 20.dp))
    }
}

/** hh h, mm m and ss s in the display face, muted until the typing has reached each. Read out as the length it sets. */
@Composable
private fun EntryDigits(entry: TimerEntry, modifier: Modifier = Modifier) {
    // With large text the three parts wrap rather than run off the card.
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = entry.label
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        EntryPart(entry.hoursText, "h", entry.hoursTyped)
        EntryPart(entry.minutesText, "m", entry.minutesTyped)
        EntryPart(entry.secondsText, "s", entry.secondsTyped)
    }
}

@Composable
private fun EntryPart(digits: String, unit: String, typed: Boolean) {
    val colors = MorrowTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(
            digits,
            style = MorrowTheme.type.displayLarge.copy(color = if (typed) colors.text else colors.muted, fontFeatureSettings = "tnum"),
            modifier = Modifier.alignByBaseline(),
        )
        BasicText(unit, style = MorrowTheme.type.bodyLarge.copy(color = colors.muted), modifier = Modifier.alignByBaseline())
    }
}

/** The chips run edge to edge on the card and scroll sideways; with large text they wrap instead, so none is hidden. */
@Composable
private fun QuickChips(onPick: (TimerEntry) -> Unit) {
    val colors = MorrowTheme.colors
    val chip = @Composable { quick: QuickTimer ->
        PillButton(
            text = quick.name,
            onClick = { onPick(quick.entry) },
            background = colors.bg,
            content = colors.text,
            border = colors.line,
            padding = PaddingValues(horizontal = 14.dp),
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
