package app.morrow.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morrow.places.City
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable

/**
 * Finding a city to add to the World tab. [model] is null until the first results are in; it can lag a little behind
 * [query] while the user is typing.
 */
@Composable
fun AddCityScreen(
    model: AddCityModel?,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: (City) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).pressable(onBack).background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(Icons.Back, colors.text, size = 22.dp, strokeWidth = 2.4f, contentDescription = "Back to World")
            }
            BasicText(
                "Add a city",
                style = MorrowTheme.type.title.copy(color = colors.text),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        SearchField(query, onQueryChange, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model != null) {
                if (model.showPopularLabel) {
                    BasicText(
                        "POPULAR",
                        style = MorrowTheme.type.label.copy(color = colors.muted),
                        modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
                    )
                }
                if (model.rows.isNotEmpty()) Results(model.rows, onAdd)
                if (model.nothingFound) {
                    BasicText("No places by that name.", style = MorrowTheme.type.body.copy(color = colors.muted), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MorrowTheme.type.bodyLarge.copy(color = colors.text),
        cursorBrush = SolidColor(colors.hi),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Search,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .background(colors.surface, shape)
            .border(2.dp, if (focused) colors.hi else colors.line, shape)
            .semantics { contentDescription = SEARCH_LABEL },
        decorationBox = { field ->
            Row(
                Modifier.heightIn(min = 52.dp).padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MorrowIcon(SEARCH, colors.muted, size = 24.dp, strokeWidth = 2.2f)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) BasicText(SEARCH_LABEL, style = MorrowTheme.type.bodyLarge.copy(color = colors.muted), maxLines = 1)
                    field()
                }
            }
        },
    )
}

@Composable
private fun Results(rows: List<AddCityRow>, onAdd: (City) -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.line, shape)) {
        rows.forEachIndexed { index, row ->
            ResultRow(row, onAdd, Modifier.then(if (index > 0) Modifier.topDivider(colors.line) else Modifier))
        }
    }
}

@Composable
private fun ResultRow(row: AddCityRow, onAdd: (City) -> Unit, modifier: Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val stacked = largeText()
    val clock = @Composable {
        BasicText(row.clock, style = type.button.copy(color = colors.muted, fontFeatureSettings = "tnum"))
    }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(row.name, style = type.bodyStrong.copy(color = colors.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(row.place, style = type.body.copy(color = colors.muted))
            if (stacked) clock()
        }
        if (!stacked) clock()
        if (row.saved) {
            Box(Modifier.size(44.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
                MorrowIcon(Icons.Check, colors.hi, size = 20.dp, strokeWidth = 3f, contentDescription = "${row.name} is already in your cities")
            }
        } else {
            Box(
                modifier = Modifier.size(44.dp).pressable({ onAdd(row.city) }).background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(Icons.Plus, colors.onAccent, size = 22.dp, strokeWidth = 2.6f, contentDescription = "Add ${row.name}")
            }
        }
    }
}

/** A hairline between rows of a list. */
private fun Modifier.topDivider(color: Color) = drawBehind { drawLine(color, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }

private const val SEARCH_LABEL = "Search for a city"
private const val SEARCH = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4"
