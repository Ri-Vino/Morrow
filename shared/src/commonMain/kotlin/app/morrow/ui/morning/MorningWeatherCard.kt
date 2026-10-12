package app.morrow.ui.morning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.pressable
import app.morrow.weather.Sky

/**
 * How it is outside and what Pip makes of it. With [onReplay], a button reads it out again; pass null when Pip
 * isn't set to read the weather aloud.
 */
@Composable
internal fun MorningWeatherCard(weather: MorningWeather, onReplay: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
                MorrowIcon(weather.icon(), colors.hi, size = 24.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(weather.headline, style = type.title.copy(color = colors.text))
                BasicText(weather.detail, style = type.body.copy(color = colors.muted))
            }
            if (onReplay != null) {
                Box(
                    modifier = Modifier.size(44.dp).pressable(onReplay).background(colors.surface2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    MorrowIcon(SpeakerIcon, colors.hi, size = 22.dp, contentDescription = REPLAY_LABEL)
                }
            }
        }
        weather.tip?.let { tip ->
            BasicText(
                "“$tip”",
                style = type.bodyLarge.copy(color = colors.text, fontFamily = type.title.fontFamily, fontStyle = FontStyle.Italic),
            )
        }
    }
}

internal const val REPLAY_LABEL = "Hear the weather again"

private fun MorningWeather.icon() = when (sky) {
    Sky.CLEAR -> if (night) Icons.Moon else Icons.Sun
    Sky.PARTLY_CLOUDY -> PartlyCloudy
    Sky.CLOUDY -> Cloud
    Sky.FOG -> Fog
    Sky.DRIZZLE -> CloudTop + "M8 18v.01M12 19v.01M16 18v.01"
    Sky.RAIN -> CloudTop + "M8.5 17L7 20.5M12.5 17L11 20.5M16.5 17L15 20.5"
    Sky.SNOW -> CloudTop + "M8 18h.01M12 18h.01M16 18h.01M10 21h.01M14 21h.01"
    Sky.STORM -> CloudTop + "M12.5 15.5L10.5 19h3l-2 3.5"
}

// Icons from the design canvas.
private const val CloudTop = "M7 14h10.5a3.6 3.6 0 0 0 .4-7.2A5.5 5.5 0 0 0 7.1 7.1 3.5 3.5 0 0 0 7 14z"
private const val PartlyCloudy = "M7.5 3v1.5M3.4 4.9l1 1M2 9h1.5M11.6 4.9l-1 1M4.8 11.4a3.5 3.5 0 0 1 5.6-4.8M9 20h8.5a3.5 3.5 0 0 0 .4-7A5 5 0 0 0 8.4 12.6 3.7 3.7 0 0 0 9 20z"
private const val Cloud = "M7 18.5h10.5a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.4 10.9 3.8 3.8 0 0 0 7 18.5z"
private const val Fog = "M4 9h16M3 13h18M5 17h14M8 5h8"
private const val SpeakerIcon = "M4 9v6h4l5 4V5L8 9H4zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"
