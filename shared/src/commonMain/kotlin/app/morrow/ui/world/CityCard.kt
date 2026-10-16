package app.morrow.ui.world

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.WeatherLayer
import app.morrow.ui.components.WeatherShapes
import app.morrow.ui.components.cityScene
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import app.morrow.ui.components.weatherIcon

/**
 * A saved city as a calm strip of its own sky at its own time of day: the name (with a house for home), how far it is
 * from here and its weather on one line, and its clock. The weather is drawn over the sky too, in the space the words
 * leave, fading out around them so nothing crosses a word. Without [weather] it shows the time of day only. A [lifted]
 * card, held to be moved, casts a deeper shadow. Screen readers get the whole card as one button.
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
    lifted: Boolean = false,
) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(24.dp)
    val scene = remember(minuteOfDay, weather?.sky) { cityScene(minuteOfDay, weather?.sky) }
    val ink = remember(scene.sky) { inkFor(scene.sky) }
    val elevation by animateDpAsState(if (lifted) 24.dp else 10.dp)
    val words = remember { WordSpots() }
    val label = "$name, ${if (isHome) "home city, " else ""}$clock, $relative" + (weather?.let { ", ${it.spoken}" } ?: "")
    val clockText = @Composable {
        BasicText(clock, style = type.display.copy(color = ink.text, fontFeatureSettings = "tnum"), modifier = words.spot("clock"))
    }
    val details = @Composable { modifier: Modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = words.spot("name"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(
                    name,
                    style = type.title.copy(color = ink.text),
                    maxLines = if (largeText()) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isHome) MorrowIcon(HOUSE, ink.text, size = 16.dp, strokeWidth = 2.4f)
            }
            FlowRow(
                modifier = words.spot("line"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
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

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .shadow(elevation, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(scene.sky)
            .background(ink.wash)
            .pressable({ onClick() }, onClickLabel = "Open $name")
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        scene.weather?.let { WeatherLayer(it, WeatherShapes.Card, Modifier.matchParentSize().clearOf(words, ink.text)) }
        val padding = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        if (largeText()) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                details(Modifier)
                clockText()
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                details(Modifier.weight(1f))
                clockText()
            }
        }
    }
}

/** Where a card's words were laid out, so its weather can keep clear of them. */
private class WordSpots {
    val spots = mutableMapOf<String, LayoutCoordinates>()

    /** The weather layer's own place, which the words are measured from. */
    var layer: LayoutCoordinates? = null

    fun spot(key: String): Modifier = Modifier.onPlaced { spots[key] = it }
}

/**
 * Wipes the weather from behind the [words], with a soft edge: clear under each word and a little way round it, then
 * coming back over [FEATHER]. Only [mask]'s alpha counts, since it wipes rather than paints.
 */
private fun Modifier.clearOf(words: WordSpots, mask: Color): Modifier =
    onPlaced { words.layer = it }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val from = words.layer?.takeIf { it.isAttached } ?: return@drawWithContent
            val margin = MARGIN.toPx()
            val feather = FEATHER.toPx()
            for (word in words.spots.values) {
                if (!word.isAttached) continue
                val box = from.localBoundingBoxOf(word)
                for (ring in 0..FEATHER_STEPS) {
                    val grow = margin + feather * ring / FEATHER_STEPS
                    drawRoundRect(
                        mask,
                        topLeft = Offset(box.left - grow, box.top - grow),
                        size = Size(box.width + 2 * grow, box.height + 2 * grow),
                        cornerRadius = CornerRadius(grow * 2),
                        alpha = if (ring == 0) 1f else FEATHER_ALPHA,
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
        }

private val MARGIN = 4.dp
private val FEATHER = 14.dp
private const val FEATHER_STEPS = 4
private const val FEATHER_ALPHA = 0.35f

private const val HOUSE = "M4 11l8-7 8 7M6 9.5V20h12V9.5"
