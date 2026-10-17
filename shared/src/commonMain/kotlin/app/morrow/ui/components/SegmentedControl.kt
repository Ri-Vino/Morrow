package app.morrow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

/** One of a few [options] side by side, the [selected] one lit, as radio buttons for screen readers. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    background: Color = MorrowTheme.colors.surface,
    border: Color? = MorrowTheme.colors.line,
    /** What TalkBack says for an option, when its label alone reads badly. */
    description: ((T) -> String)? = null,
) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .background(background, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val fill by animateColorAsState(if (on) colors.accent else Color.Transparent)
            val content by animateColorAsState(if (on) colors.onAccent else colors.muted)
            val outline by animateColorAsState(if (on) colors.hi else Color.Transparent)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .selectable(on, role = Role.RadioButton) { onSelect(option) }
                    .then(if (description != null) Modifier.semantics { contentDescription = description(option) } else Modifier)
                    .background(fill, CircleShape)
                    .border(2.dp, outline, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label(option), style = MorrowTheme.type.bodyStrong.copy(color = content, textAlign = TextAlign.Center))
            }
        }
    }
}
