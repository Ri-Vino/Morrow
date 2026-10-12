package app.morrow.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.CityScene
import app.morrow.ui.components.cityScene
import app.morrow.weather.Sky

/**
 * The design canvas's "City card states" artboard: a city card's scene at the twelve times and weathers it draws,
 * two to a row, to hold up against the canvas on a phone.
 */
@Composable
fun CityCardStates(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DebugButton("← Back", onClick = onBack)
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("WORLD TAB", style = type.label.copy(color = colors.muted))
            BasicText("City card states", style = type.title.copy(color = colors.text))
            BasicText("The sky follows the city’s own time of day, and the weather draws over it.", style = type.body.copy(color = colors.muted))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardStates.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { state -> StateCard(state, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StateCard(state: CardState, modifier: Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val shape = RoundedCornerShape(20.dp)
    val scene = remember(state) { cityScene(state.minute, state.sky) }
    Column(modifier.clip(shape).background(colors.surface).border(1.dp, colors.line, shape)) {
        CityScene(scene, Modifier.fillMaxWidth().aspectRatio(342f / 128f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(state.title, style = type.bodyStrong.copy(color = colors.text), modifier = Modifier.weight(1f))
            BasicText(
                "%02d:%02d".format(state.minute / 60, state.minute % 60),
                style = type.bodyStrong.copy(color = colors.muted, fontFeatureSettings = "tnum"),
            )
        }
    }
}

private data class CardState(val title: String, val sky: Sky, val minute: Int)

/** The canvas's CARD_STATES, in its order. */
private val CardStates = listOf(
    CardState("Clear · midday", Sky.CLEAR, 750),
    CardState("Clear · dawn", Sky.CLEAR, 400),
    CardState("Clear · night", Sky.CLEAR, 1380),
    CardState("Partly cloudy", Sky.PARTLY_CLOUDY, 600),
    CardState("Cloudy", Sky.CLOUDY, 840),
    CardState("Fog", Sky.FOG, 480),
    CardState("Drizzle", Sky.DRIZZLE, 660),
    CardState("Rain · day", Sky.RAIN, 600),
    CardState("Rain · night", Sky.RAIN, 1320),
    CardState("Thunderstorm", Sky.STORM, 960),
    CardState("Snow · day", Sky.SNOW, 720),
    CardState("Snow · night", Sky.SNOW, 1350),
)
