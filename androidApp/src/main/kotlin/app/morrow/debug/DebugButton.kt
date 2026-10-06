package app.morrow.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

@Composable
fun DebugButton(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    BasicText(
        text = text,
        style = MorrowTheme.type.button.copy(
            color = if (selected) colors.text else colors.hi,
            textDecoration = if (selected) null else TextDecoration.Underline,
        ),
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp)
            .wrapContentSize(),
    )
}
