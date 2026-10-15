package app.morrow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.theme.mix

/** The design's on/off switch: 54 × 32, inside a 48 dp tall touch target. */
@Composable
fun MorrowToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.line)
    // On, an outline in the highlight colour so "on" still reads in the light themes. Off, a darker outline, since
    // the bare track is too close to the cards behind it to see.
    val outline by animateColorAsState(if (checked) colors.hi else mix(colors.line, colors.muted, 0.7f))
    val knob by animateColorAsState(if (checked) colors.onAccent else colors.knobOff)
    val knobOffset by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(54.dp, 32.dp)
                .background(track, CircleShape)
                .border(2.dp, outline, CircleShape),
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    .offset(x = knobOffset)
                    .size(24.dp)
                    .background(knob, CircleShape),
            )
        }
    }
}
