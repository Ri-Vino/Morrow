package app.morrow.ui.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.morrow.missions.TypeMission
import app.morrow.missions.TypeState
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme

private const val FIELD_LABEL = "Your sentence"

@Composable
internal fun TypeBody(state: TypeState, mission: TypeMission, autoFocus: Boolean = false) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val typo = state.typoWord != null
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText("TYPE THIS", style = type.label.copy(color = colors.muted))
            BasicText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.hi, textDecoration = TextDecoration.Underline)) {
                        append(state.sentence.take(state.matched))
                    }
                    append(state.sentence.drop(state.matched))
                },
                style = type.title.copy(color = colors.text, lineHeight = type.title.fontSize * 1.35f),
            )
        }

        BasicText(FIELD_LABEL, style = type.bodyStrong.copy(color = colors.muted))
        val shape = RoundedCornerShape(22.dp)
        BasicTextField(
            value = state.typed,
            onValueChange = mission::type,
            singleLine = true,
            textStyle = type.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(colors.hi),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .heightIn(min = 64.dp)
                .background(colors.bg, shape)
                .border(2.dp, if (typo) Illustration.mistake else colors.line, shape)
                .semantics { contentDescription = FIELD_LABEL },
            decorationBox = { field ->
                Box(Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { field() }
            },
        )

        BasicText(
            typeHint(state),
            style = type.body.copy(color = if (typo) colors.text else colors.muted),
            modifier = Modifier
                .heightIn(min = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
