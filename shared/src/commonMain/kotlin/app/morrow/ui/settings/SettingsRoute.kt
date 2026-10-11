package app.morrow.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.data.MorrowSettings
import app.morrow.places.CityRepository
import app.morrow.platform.PermissionGate
import app.morrow.theme.ThemeKind
import app.morrow.ui.PlatformBackHandler
import app.morrow.weather.HerePlace
import app.morrow.weather.WeatherPlaceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Settings over the stored settings. [here] is where "Where I am" weather is for, [phoneCountry] the phone's region
 * for the default units, and [onWeatherChanged] hears when weather is turned on or its place changes, to fetch it.
 */
@Composable
fun SettingsRoute(
    settings: MorrowSettings,
    pinnedTheme: StateFlow<ThemeKind?>,
    theme: ThemeKind,
    permissions: PermissionGate,
    cities: CityRepository,
    here: Flow<HerePlace?>,
    phoneCountry: String,
    developerTools: (() -> Unit)?,
    onPickHomeCity: () -> Unit,
    onWeatherChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pinned by pinnedTheme.collectAsStateWithLifecycle()
    val missing by permissions.missing.collectAsStateWithLifecycle()
    val missions by settings.missionSetup.collectAsStateWithLifecycle(initialValue = null)
    val alarm by settings.alarmSetup.collectAsStateWithLifecycle(initialValue = null)
    val weatherOn by settings.weatherEnabled.collectAsStateWithLifecycle(initialValue = null)
    val mode by settings.weatherSource.collectAsStateWithLifecycle(initialValue = WeatherPlaceMode.AUTO)
    val units by remember(phoneCountry) { settings.effectiveUnits(phoneCountry) }.collectAsStateWithLifecycle(initialValue = null)
    val herePlace by here.collectAsStateWithLifecycle(initialValue = null)
    val homeName by remember(cities) { cities.cities.map { saved -> saved.firstOrNull { it.isHome }?.city?.name } }
        .collectAsStateWithLifecycle(initialValue = null)
    PlatformBackHandler(onBack = onBack)

    val difficulty = missions?.difficulty ?: return
    val sound = alarm ?: return
    val enabled = weatherOn ?: return
    val shownUnits = units ?: return
    SettingsScreen(
        model = settingsModel(pinned, theme, difficulty, permissions.asked, missing),
        choices = SettingsChoices(difficulty, sound.sound, sound.ramp),
        weather = weatherSection(enabled, mode, herePlace, homeName, shownUnits),
        actions = SettingsActions(
            onBack = onBack,
            // Turning "follow the clock" off keeps the theme showing now.
            onFollowClock = { follow -> scope.launch { settings.setPinnedTheme(if (follow) null else theme) } },
            onPinTheme = { scope.launch { settings.setPinnedTheme(it) } },
            onDifficulty = { scope.launch { settings.setDifficulty(it) } },
            onSound = { scope.launch { settings.setSound(it) } },
            onRamp = { scope.launch { settings.setRamp(it) } },
            onAllow = permissions::request,
            onDeveloperTools = developerTools,
            onWeatherEnabled = { on ->
                scope.launch {
                    settings.setWeatherEnabled(on)
                    if (on) onWeatherChanged()
                }
            },
            onWeatherMode = { picked ->
                scope.launch {
                    settings.setWeatherSource(picked)
                    onWeatherChanged()
                }
            },
            onPickHomeCity = onPickHomeCity,
            onUnits = { scope.launch { settings.setUnits(it) } },
        ),
    )
}
