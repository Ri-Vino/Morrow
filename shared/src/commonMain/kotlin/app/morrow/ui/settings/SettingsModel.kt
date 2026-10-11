package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.theme.ThemeKind
import app.morrow.weather.HerePlace
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode

data class SettingsModel(
    val followClock: Boolean,
    val followDescription: String,
    /** The theme showing now: the pinned one, or the clock's. */
    val shownTheme: ThemeKind,
    /** What the chosen difficulty means for a round. */
    val difficultyLevel: String,
    val difficultyDescription: String,
    val permissions: List<PermissionStatus>,
)

data class PermissionStatus(val permission: AlarmPermission, val granted: Boolean)

fun settingsModel(
    pinned: ThemeKind?,
    byClock: ThemeKind,
    difficulty: Difficulty,
    asked: List<AlarmPermission>,
    missing: List<AlarmPermission>,
): SettingsModel {
    val shown = pinned ?: byClock
    return SettingsModel(
        followClock = pinned == null,
        followDescription = if (pinned == null) "Now showing ${shown.displayName}" else "Pinned to ${shown.displayName}",
        shownTheme = shown,
        difficultyLevel = when (difficulty) {
            Difficulty.GENTLE -> "Gentler rounds: small sums, short sentences, soft shakes."
            Difficulty.NORMAL -> "Everyday rounds: two-digit sums and short sentences."
            Difficulty.TOUGH -> "Tougher rounds: bigger sums, longer sentences, firm shakes."
        },
        difficultyDescription = "How hard each round is. The number of rounds is set on each alarm.",
        permissions = asked.map { PermissionStatus(it, granted = it !in missing) },
    )
}

/** Settings › Weather. */
data class WeatherSection(
    val enabled: Boolean,
    val mode: WeatherPlaceMode,
    /** What the chosen mode means, and for "Where I am", which place it's using now. */
    val description: String,
    val homeCityName: String,
    val units: Units,
)

/**
 * The Weather section. [here] is where "Where I am" weather is for now, and [homeCity] the home city's name, both
 * null until known.
 */
fun weatherSection(enabled: Boolean, mode: WeatherPlaceMode, here: HerePlace?, homeCity: String?, units: Units): WeatherSection =
    WeatherSection(
        enabled = enabled,
        mode = mode,
        description = when (mode) {
            WeatherPlaceMode.AUTO -> "Your rough location while Morrow is open, or the last place it knew." +
                (here?.name?.let { " Now: $it." } ?: "")
            WeatherPlaceMode.HOME -> "Always ${homeCity ?: "your home city"}, wherever you are."
        },
        homeCityName = homeCity ?: "None yet",
        units = units,
    )

val WeatherPlaceMode.displayName: String
    get() = when (this) {
        WeatherPlaceMode.AUTO -> "Where I am"
        WeatherPlaceMode.HOME -> "Home city"
    }

val Units.symbol: String
    get() = when (this) {
        Units.CELSIUS -> "°C"
        Units.FAHRENHEIT -> "°F"
    }

val ThemeKind.shortName: String
    get() = when (this) {
        ThemeKind.MORNING -> "Morning"
        ThemeKind.NOON -> "Noon"
        ThemeKind.AFTERNOON -> "Afternoon"
        ThemeKind.NIGHT -> "Night"
    }
