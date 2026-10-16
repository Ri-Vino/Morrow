package app.morrow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

/**
 * The number pad of the Sums game and the time and length sheets: 1 to 9 in three rows, then a bottom row of the
 * caller's keys. [onDigit] hears each of 1 to 9 that [enabled] allows; the others are dimmed and do nothing. The bottom
 * row's keys say for themselves what they do.
 */
@Composable
fun Keypad(
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    keyBackground: Color = MorrowTheme.colors.surface,
    minKeyHeight: Dp = 56.dp,
    enabled: (Int) -> Boolean = { true },
    bottomRow: @Composable KeypadRow.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    KeypadKey({ onDigit(digit) }, keyBackground, label = null, minKeyHeight, enabled(digit)) { DigitLabel(digit.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadRow(this, keyBackground, minKeyHeight).bottomRow()
        }
    }
}

/** The keys of a [Keypad]'s bottom row, which take the pad's colour and height. */
class KeypadRow internal constructor(
    private val row: RowScope,
    private val background: Color,
    private val minHeight: Dp,
) {
    /** A key showing [text] in the display face, read out as the text itself. */
    @Composable
    fun Digit(text: String, onClick: () -> Unit) {
        with(row) { KeypadKey(onClick, background, label = null, minHeight, enabled = true) { DigitLabel(text) } }
    }

    /** A key showing [content], an icon for instance, which [label] describes to screen readers. */
    @Composable
    fun Key(label: String, onClick: () -> Unit, background: Color = this.background, content: @Composable () -> Unit) {
        with(row) { KeypadKey(onClick, background, label, minHeight, enabled = true, content) }
    }

    /** An empty place, the width of a key. */
    @Composable
    fun Gap() {
        with(row) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun RowScope.KeypadKey(
    onClick: () -> Unit,
    background: Color,
    label: String?,
    minHeight: Dp,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressable({ if (enabled) onClick() })
            .background(background, RoundedCornerShape(20.dp))
            .semantics {
                if (label != null) contentDescription = label
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DigitLabel(text: String) {
    BasicText(text, style = MorrowTheme.type.title.copy(color = MorrowTheme.colors.text))
}

private const val DISABLED_ALPHA = 0.35f
