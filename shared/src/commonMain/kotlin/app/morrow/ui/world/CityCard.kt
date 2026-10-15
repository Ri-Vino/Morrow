package app.morrow.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.cityScene
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import app.morrow.ui.components.weatherIcon

/**
 * A saved city as a calm strip of its own sky at its own time of day: the name (with a house for home), how far it is
 * from here and its weather on one line, and its clock. Without [weather] it shows the time of day only. Screen readers
 * get the whole card as one button.
 */
@Composable
fun CityCard(
    name: String,
    isHome: Boolean,
    relative: String,
    clock: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weather: CardWeather? = null,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(24.dp)
    val scene = remember(minuteOfDay, weather?.sky) { cityScene(minuteOfDay, weather?.sky) }
    val ink = remember(scene.sky) { inkFor(scene.sky) }
    val label = "$name, ${if (isHome) "home city, " else ""}$clock, $relative" + (weather?.let { ", ${it.spoken}" } ?: "")
    val clockText = @Composable { BasicText(clock, style = type.display.copy(color = ink.text, fontFeatureSettings = "tnum")) }
    val details = @Composable { modifier: Modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    name,
                    style = type.title.copy(color = ink.text),
                    maxLines = if (largeText()) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isHome) MorrowIcon(HOUSE, ink.text, size = 16.dp, strokeWidth = 2.4f)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                BasicText(relative, style = type.body.copy(color = ink.text))
                weather?.let {
                    BasicText("·", style = type.body.copy(color = ink.text))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MorrowIcon(weatherIcon(it.sky, it.night), ink.text, size = 16.dp, strokeWidth = 2.2f)
                        BasicText(it.conditions, style = type.body.copy(color = ink.text))
                    }
                }
            }
        }
    }

    val card = modifier
        .fillMaxWidth()
        .heightIn(min = 84.dp)
        .shadow(10.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
        .clip(shape)
        .background(scene.sky)
        .background(ink.wash)
        .pressable({ onClick() }, onClickLabel = "Open $name")
        .semantics(mergeDescendants = true) { contentDescription = label }
        .padding(horizontal = 20.dp, vertical = 14.dp)
    if (largeText()) {
        Column(card, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            details(Modifier)
            clockText()
        }
    } else {
        Row(card, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            details(Modifier.weight(1f))
            clockText()
        }
    }
}

private const val HOUSE = "M4 11l8-7 8 7M6 9.5V20h12V9.5"
