package app.morrow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

/**
 * One part of a time or length being typed: its two [digits], how many of them are zeros [waiting] to be typed over,
 * its [name] for screen readers, and the [unit] shown after it, if any.
 */
data class ReadoutPart(val digits: String, val waiting: Int, val name: String, val unit: String? = null)

/**
 * A time or length being typed on a [Keypad], large: its [parts] side by side, with [separator] between them or each
 * followed by its unit. Zeros still waiting to be typed over are muted. With [onPick], each part is a button that picks
 * it to type into, and the [active] one is outlined.
 */
@Composable
fun EntryReadout(
    parts: List<ReadoutPart>,
    modifier: Modifier = Modifier,
    separator: String? = null,
    active: Int? = null,
    onPick: ((Int) -> Unit)? = null,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val digitStyle = (if (largeText()) type.display else type.displayLarge).copy(color = colors.text, fontFeatureSettings = "tnum")
    val shape = RoundedCornerShape(20.dp)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { i, part ->
            if (i > 0 && separator != null) BasicText(separator, style = digitStyle.copy(color = colors.muted))
            val picked = active == i
            val pickable = onPick?.let { pick ->
                Modifier
                    .clip(shape)
                    .pressable({ pick(i) }, onClickLabel = "Type the ${part.name.lowercase()}")
                    .background(colors.surface, shape)
                    .border(2.dp, if (picked) colors.hi else colors.line, shape)
                    .semantics {
                        contentDescription = "${part.name}, ${part.digits}"
                        selected = picked
                    }
            } ?: Modifier
            Row(pickable.padding(horizontal = 14.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val digits = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.muted)) { append(part.digits.take(part.waiting)) }
                    append(part.digits.drop(part.waiting))
                }
                BasicText(digits, style = digitStyle, modifier = Modifier.alignByBaseline())
                part.unit?.let { BasicText(it, style = type.title.copy(color = colors.muted), modifier = Modifier.alignByBaseline()) }
            }
        }
    }
}
