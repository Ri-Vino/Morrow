package app.morrow.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.CityScene
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.cityScene
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import app.morrow.ui.components.weatherIcon

/**
 * A saved city: its own sky at its own time of day under its [weather], with the name, how far it is from here, its
 * clock, and the weather over the top. Without [weather] it shows the time of day only. Screen readers get the whole
 * card as one button.
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
    val shape = RoundedCornerShape(28.dp)
    val scene = remember(minuteOfDay, weather?.sky) { cityScene(minuteOfDay, weather?.sky) }
    val ink = remember(scene.sky) { inkFor(scene.sky) }
    val wash = remember(ink) {
        Brush.horizontalGradient(
            0f to ink.scrim.copy(alpha = ink.scrimLeft),
            SCRIM_CLEAR_AT to ink.scrim.copy(alpha = 0f),
            1f to ink.scrim.copy(alpha = ink.scrimRight),
        )
    }
    val stacked = largeText()
    val label = "$name, ${if (isHome) "home city, " else ""}$clock, $relative" + (weather?.let { ", ${it.spoken}" } ?: "")
    val clockText = @Composable { BasicText(clock, style = type.display.copy(color = ink.text, fontFeatureSettings = "tnum")) }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .pressable({ onClick() }, onClickLabel = "Open $name")
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        CityScene(scene, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(wash))
        if (stacked) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Details(name, isHome, relative, ink, stacked = true)
                clockText()
                weather?.let {
                    Conditions(it, ink)
                    HighLow(it, ink)
                }
            }
        } else {
            // The weather sits along the bottom of the card, under the name on the left and the clock on the right.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 128.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Details(name, isHome, relative, ink, stacked = false)
                    weather?.let { Conditions(it, ink) }
                }
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                    clockText()
                    weather?.let { HighLow(it, ink) }
                }
            }
        }
    }
}

@Composable
private fun Details(name: String, isHome: Boolean, relative: String, ink: CardInk, stacked: Boolean) {
    val type = MorrowTheme.type
    val nameText: @Composable (Modifier) -> Unit = { modifier ->
        BasicText(
            name,
            style = type.title.copy(color = ink.text),
            maxLines = if (stacked) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (stacked) {
            nameText(Modifier)
            if (isHome) HomeChip(ink)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                nameText(Modifier.weight(1f, fill = false))
                if (isHome) HomeChip(ink)
            }
        }
        BasicText(relative, style = type.body.copy(color = ink.text))
    }
}

@Composable
private fun Conditions(weather: CardWeather, ink: CardInk) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MorrowIcon(weatherIcon(weather.sky, weather.night), ink.text, strokeWidth = 2.2f)
        BasicText(weather.conditions, style = MorrowTheme.type.button.copy(color = ink.text))
    }
}

@Composable
private fun HighLow(weather: CardWeather, ink: CardInk) {
    BasicText(weather.highLow, style = MorrowTheme.type.bodyStrong.copy(color = ink.text))
}

@Composable
private fun HomeChip(ink: CardInk) {
    Row(
        Modifier.background(ink.chip, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MorrowIcon(HOUSE, ink.text, size = 12.dp, strokeWidth = 2.6f)
        BasicText("Home", style = MorrowTheme.type.label.copy(color = ink.text, letterSpacing = MorrowTheme.type.caption.letterSpacing))
    }
}

private const val HOUSE = "M4 11l8-7 8 7M6 9.5V20h12V9.5"
