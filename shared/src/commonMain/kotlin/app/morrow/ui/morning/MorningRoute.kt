package app.morrow.ui.morning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.morrow.data.MorrowSettings
import app.morrow.platform.Speaker
import app.morrow.platform.SpeechPurpose
import app.morrow.weather.Units
import app.morrow.weather.WeatherRepository
import app.morrow.weather.WeatherState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What the Morning screen shows besides the moment it opens: a guess at bedtime, the weather, its units, and whether
 * Pip reads it out.
 */
data class MorningInputs(val wentToBed: Instant?, val forecasts: WeatherState, val units: Units, val readAloud: Boolean)

/** [MorningInputs] as they change. Collect it while the alarm rings and pass the latest to [MorningRoute]. */
fun morningInputs(settings: MorrowSettings, weather: WeatherRepository, phoneCountry: String): Flow<MorningInputs> =
    combine(settings.lastClosedAt, weather.forecasts, settings.effectiveUnits(phoneCountry), settings.readWeatherAloud) { bed, state, units, aloud ->
        MorningInputs(bed, state, units, aloud)
    }

/**
 * The Morning screen for a mission finished at [wokeAt]. [onStartDay] gets what the user typed, trimmed. With
 * [preloaded] inputs from [morningInputs], it opens complete, rather than a frame or two before its weather.
 *
 * Opening it refreshes the weather, for up to 5 s, or takes up the refresh [WeatherRepository.prepareMorning] began
 * as the alarm rang. The card shows as soon as there is a forecast from today, and a
 * fresh fetch replaces it in place; with nothing from today, it stays hidden. With "Pip reads it out when you wake"
 * on, [speaker] reads the card once after the refresh, and a button reads it again; leaving the screen or "Start my
 * day" cuts off only what this screen is saying. [phoneCountry] is the phone's region, for the default units.
 */
@Composable
fun MorningRoute(
    settings: MorrowSettings,
    weather: WeatherRepository,
    speaker: Speaker,
    phoneCountry: String,
    wokeAt: Instant,
    onStartDay: (intention: String) -> Unit,
    preloaded: MorningInputs? = null,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val inputs by remember(settings, weather, phoneCountry) { morningInputs(settings, weather, phoneCountry) }
        .collectAsState(initial = preloaded)
    val forecasts = inputs?.forecasts
    val units = inputs?.units
    val readAloud = inputs?.readAloud
    val model = morningModel(wokeAt, inputs?.wentToBed, zone)

    var refreshed by remember { mutableStateOf(false) }
    var stored by remember { mutableStateOf<Instant?>(null) }
    LaunchedEffect(weather) {
        stored = try {
            weather.refreshForMorning()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The weather is a nicety here; whatever is cached stays.
            null
        }
        refreshed = true
    }
    // The database passes a new forecast on a moment after storing it, so the refresh is done once the card has it.
    val shownFetchedAt = forecasts?.hereWeather?.forecast?.fetchedAt
    val settled = refreshed && stored.let { it == null || (shownFetchedAt != null && shownFetchedAt >= it) }

    val now = remember(forecasts) { clock.now() }
    val card = remember(forecasts, units, now, zone) {
        val state = forecasts
        val shownIn = units
        if (state != null && shownIn != null) morningWeather(state, shownIn, now, zone) else null
    }
    val speech = card?.speech(model.hello)
    val latestSpeech by rememberUpdatedState(speech)

    var spoken by rememberSaveable { mutableStateOf(false) }
    var talking by remember { mutableIntStateOf(0) }
    val say: suspend (String) -> Unit = { text ->
        talking++
        try {
            speaker.speak(text, SpeechPurpose.MORNING_WEATHER)
        } finally {
            talking--
        }
    }
    // Waits for the refresh, so Pip reads the weather the card ends up showing, and reads it only once.
    LaunchedEffect(speech != null, readAloud, settled) {
        val text = latestSpeech
        if (text != null && readAloud == true && settled && !spoken) {
            spoken = true
            say(text)
        }
    }
    DisposableEffect(speaker) { onDispose { if (talking > 0) speaker.stop(SpeechPurpose.MORNING_WEATHER) } }

    val scope = rememberCoroutineScope()
    val replay: (() -> Unit)? = if (speech != null && readAloud == true) {
        { scope.launch { say(speech) } }
    } else {
        null
    }
    MorningScreen(
        model = model,
        weather = card,
        onReplay = replay,
        onStartDay = { intention ->
            if (talking > 0) speaker.stop(SpeechPurpose.MORNING_WEATHER)
            onStartDay(intention)
        },
    )
}
