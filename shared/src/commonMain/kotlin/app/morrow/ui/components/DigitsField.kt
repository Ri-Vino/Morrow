package app.morrow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

/**
 * Up to two digits on the number keyboard, in the display face: hours, minutes or seconds. [label] names it for
 * TalkBack, and [onIme] runs on the keyboard's action key.
 */
@Composable
fun DigitsField(
    value: String,
    label: String,
    onValue: (String) -> Unit,
    imeAction: ImeAction,
    onIme: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(20.dp)
    var focused by remember { mutableStateOf(false) }
    // Focusing a field selects what's in it, so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = field,
        onValueChange = { changed ->
            val digits = changed.text.filter(Char::isDigit).take(2)
            field = changed.copy(text = digits, selection = if (digits == changed.text) changed.selection else TextRange(digits.length))
            if (digits != value) onValue(digits)
        },
        singleLine = true,
        textStyle = MorrowTheme.type.displayLarge.copy(color = colors.text, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
        cursorBrush = SolidColor(colors.hi),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = modifier
            .width(width)
            .background(colors.surface, shape)
            .border(2.dp, if (focused) colors.hi else colors.line, shape)
            .onFocusChanged { state ->
                if (state.isFocused && !focused) field = field.copy(selection = TextRange(0, field.text.length))
                focused = state.isFocused
            }
            .padding(vertical = 8.dp)
            .semantics { contentDescription = label },
    )
}
