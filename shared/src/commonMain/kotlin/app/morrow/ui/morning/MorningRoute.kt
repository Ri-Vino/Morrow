package app.morrow.ui.morning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.morrow.data.MorrowSettings
import app.morrow.platform.Speaker
import app.morrow.platform.SpeechPurpose
import app.morrow.weather.RefreshReason
import app.morrow.weather.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Morning screen for a mission finished at [wokeAt]. [onStartDay] gets what the user typed, trimmed.
 *
 * Opening it refreshes the weather, for up to 5 s. The card shows as soon as there is a forecast from today, and a
 * fresh fetch replaces it in place; with nothing from today, it stays hidden. With "Pip reads it out when you wake"
 * on, [speaker] reads the card once after the refresh, and a button reads it again. [phoneCountry] is the phone's
 * region, for the default units.
 */
@Composable
fun MorningRoute(
    settings: MorrowSettings,
    weather: WeatherRepository,
    speaker: Speaker,
    phoneCountry: String,
    wokeAt: Instant,
    onStartDay: (intention: String) -> Unit,
    clock: Clock = Clock.System,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val wentToBed by remember(settings) { settings.lastClosedAt }.collectAsState(initial = null)
    val forecasts by remember(weather) { weather.forecasts }.collectAsState(initial = null)
    val units by remember(settings, phoneCountry) { settings.effectiveUnits(phoneCountry) }.collectAsState(initial = null)
    val readAloud by remember(settings) { settings.readWeatherAloud }.collectAsState(initial = null)
    val model = morningModel(wokeAt, wentToBed, zone)

    var refreshed by remember { mutableStateOf(false) }
    LaunchedEffect(weather) {
        try {
            weather.refresh(RefreshReason.MORNING)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The weather is a nicety here; whatever is cached stays.
        }
        refreshed = true
    }

    val now = remember(forecasts) { clock.now() }
    val card = remember(forecasts, units, now, zone) {
        val state = forecasts
        val shownIn = units
        if (state != null && shownIn != null) morningWeather(state, shownIn, now, zone) else null
    }
    val speech = card?.speech(model.hello)
    val latestSpeech by rememberUpdatedState(speech)

    var spoken by rememberSaveable { mutableStateOf(false) }
    var talking by remember { mutableStateOf(false) }
    // Waits for the refresh, so Pip reads the weather the card ends up showing, and reads it only once.
    LaunchedEffect(speech != null, readAloud, refreshed) {
        val text = latestSpeech
        if (text != null && readAloud == true && refreshed && !spoken) {
            spoken = true
            talking = true
            speaker.speak(text, SpeechPurpose.MORNING_WEATHER)
        }
    }
    DisposableEffect(speaker) { onDispose { if (talking) speaker.stop() } }

    val scope = rememberCoroutineScope()
    val replay: (() -> Unit)? = if (speech != null && readAloud == true) {
        {
            talking = true
            scope.launch { speaker.speak(speech, SpeechPurpose.MORNING_WEATHER) }
        }
    } else {
        null
    }
    MorningScreen(
        model = model,
        weather = card,
        onReplay = replay,
        onStartDay = { intention ->
            if (talking) speaker.stop()
            onStartDay(intention)
        },
    )
}
