package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.platform.VoiceInfo
import app.morrow.theme.ThemeKind
import app.morrow.weather.HerePlace
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode

data class SettingsModel(
    /** What the chosen difficulty means for a round. */
    val difficultyLevel: String,
    val difficultyDescription: String,
    val permissions: List<PermissionStatus>,
)

data class PermissionStatus(val permission: AlarmPermission, val granted: Boolean)

fun settingsModel(
    difficulty: Difficulty,
    asked: List<AlarmPermission>,
    missing: List<AlarmPermission>,
): SettingsModel {
    return SettingsModel(
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
    /** Whether Pip reads the weather out on the Morning screen. */
    val readAloud: Boolean,
)

/**
 * The Weather section. [here] is where "Where I am" weather is for now, and [homeCity] the home city's name, both
 * null until known.
 */
fun weatherSection(
    enabled: Boolean,
    mode: WeatherPlaceMode,
    here: HerePlace?,
    homeCity: String?,
    units: Units,
    readAloud: Boolean,
): WeatherSection =
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
        readAloud = readAloud,
    )

/** One row of the voice picker. [id] is null for the phone's default. */
data class VoiceChoice(val id: String?, val title: String, val detail: String?, val selected: Boolean)

/** Settings › Voice. */
data class VoiceSection(
    /** What "Pip’s voice" is set to now, or that the phone has none. */
    val current: String,
    val choices: List<VoiceChoice>,
    /** Shown in the picker when the phone has no voices. */
    val emptyNote: String?,
)

/** Pip's reply when a voice is picked. */
const val VOICE_PICKED_PHRASE = "Hello, I’m Pip. This is how I’ll sound."

/** What "Hear a sample" says. */
const val VOICE_SAMPLE_PHRASE = "Good morning! It’s twelve degrees and rainy in London. Take an umbrella."

private const val PHONE_DEFAULT = "Phone’s default"

/**
 * The Voice section. [voiceId] is the saved choice, null for the phone's default, and [voices] what the phone offers,
 * null until known. A saved voice that is no longer installed counts as the default, which is what Pip then uses.
 */
fun voiceSection(voiceId: String?, voices: List<VoiceInfo>?): VoiceSection {
    val picked = voices?.firstOrNull { it.id == voiceId }
    val language = voices?.firstOrNull()?.language
    val default = VoiceChoice(null, PHONE_DEFAULT, language, selected = picked == null)
    return VoiceSection(
        current = when {
            voices != null && voices.isEmpty() -> "No voices on this phone"
            picked != null -> picked.name
            language != null -> "$PHONE_DEFAULT · $language"
            else -> PHONE_DEFAULT
        },
        choices = listOf(default) + voices.orEmpty().map { VoiceChoice(it.id, it.name, null, selected = it.id == picked?.id) },
        emptyNote = if (voices != null && voices.isEmpty()) {
            "This phone has no voice for Pip, so Pip stays quiet. The words stay on screen."
        } else {
            null
        },
    )
}

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
