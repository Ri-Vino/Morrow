package app.morrow.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.BottomSheet
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.SecondaryButton

/**
 * Asked when a temporary alarm, such as a nap, is switched off: it has no "off" to come back to, so switching it off
 * removes it. [onRemove] removes it, and Home then offers Undo; [onKeep] leaves it on.
 */
@Composable
fun RemoveTemporarySheet(time: String, onRemove: () -> Unit, onKeep: () -> Unit) {
    val colors = MorrowTheme.colors
    BottomSheet("Remove alarm?", onKeep) {
        BasicText("Remove alarm?", style = MorrowTheme.type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        BasicText(
            "Switching off a temporary alarm removes it. The $time alarm won’t ring, and you can undo this right after.",
            style = MorrowTheme.type.body.copy(color = colors.muted),
        )
        PillButton("Remove alarm", onRemove, colors.warn, colors.onWarn, Modifier.fillMaxWidth(), height = 56.dp)
        SecondaryButton("Keep it on", onKeep)
    }
}
