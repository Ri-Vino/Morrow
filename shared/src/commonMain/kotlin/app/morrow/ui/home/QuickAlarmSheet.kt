package app.morrow.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.domain.DEFAULT_NAP_MINUTES
import app.morrow.domain.MAX_QUICK_MINUTES
import app.morrow.domain.NapChoices
import app.morrow.domain.QuickChoices
import app.morrow.domain.typeMinutes
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.BottomSheet
import app.morrow.ui.components.EntryReadout
import app.morrow.ui.components.Icons
import app.morrow.ui.components.Keypad
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.ReadoutPart
import app.morrow.ui.components.SegmentedControl
import app.morrow.ui.hhmm

/**
 * How long until a temporary alarm rings: a nap, or [QuickKind.IN_MINUTES]. Quick picks, or any number of minutes typed,
 * and a button that says when it will ring, from [minuteOfDay] now. [onSet] hears the minutes.
 */
@Composable
fun QuickAlarmSheet(kind: QuickKind, minuteOfDay: Float, onSet: (Int) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val nap = kind == QuickKind.NAP
    val choices = if (nap) NapChoices else QuickChoices
    var typed by rememberSaveable { mutableStateOf(if (nap) DEFAULT_NAP_MINUTES.toString() else "") }
    // A quick pick shows in the readout, but the next digit typed starts a new number rather than adding to it.
    var picked by rememberSaveable { mutableStateOf(nap) }
    val minutes = typed.toIntOrNull()?.takeIf { it in 1..MAX_QUICK_MINUTES }
    val title = if (nap) "Nap" else "Ring in"
    BottomSheet(title, onClose) {
        BasicText(title, style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        SegmentedControl<Int?>(
            options = choices,
            selected = minutes,
            label = { it.toString() },
            onSelect = {
                typed = it.toString()
                picked = true
            },
            description = { "$it minutes" },
        )
        EntryReadout(
            parts = listOf(ReadoutPart(typed.ifEmpty { "0" }, waiting = if (typed.isEmpty()) 1 else 0, name = "Minutes", unit = "min")),
        )
        Keypad(onDigit = {
            typed = typeMinutes(if (picked) "" else typed, it)
            picked = false
        }) {
            Gap()
            Digit("0") {
                typed = typeMinutes(if (picked) "" else typed, 0)
                picked = false
            }
            Key("Delete digit", {
                typed = typed.dropLast(1)
                picked = false
            }) { MorrowIcon(Icons.DeleteDigit, colors.muted, size = 28.dp) }
        }
        val ringsAt = minutes?.let { (minuteOfDay.toInt() + it) % (24 * 60) }?.let { hhmm(it / 60, it % 60) }
        PillButton(
            text = when {
                ringsAt == null -> "Type up to $MAX_QUICK_MINUTES minutes"
                nap -> "Nap until $ringsAt"
                else -> "Ring at $ringsAt"
            },
            onClick = { minutes?.let(onSet) },
            background = if (minutes != null) colors.accent else colors.surface2,
            content = if (minutes != null) colors.onAccent else colors.muted,
            modifier = Modifier.fillMaxWidth().semantics { if (minutes == null) disabled() },
            height = 56.dp,
        )
        BasicText(
            "Rings once, then deletes itself once you’re up.",
            style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
