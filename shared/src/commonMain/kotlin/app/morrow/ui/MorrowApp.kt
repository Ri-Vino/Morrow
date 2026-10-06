package app.morrow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.alarms.AlarmCoordinator
import app.morrow.data.AlarmRepository
import app.morrow.data.MorrowSettings
import app.morrow.domain.Alarm
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeClock
import app.morrow.theme.ThemeKind
import app.morrow.theme.isLightSky
import app.morrow.ui.firstrun.SetupScreen
import app.morrow.ui.firstrun.WelcomeScreen
import app.morrow.ui.home.HomeActions
import app.morrow.ui.home.HomeScreen
import app.morrow.ui.home.homeModel
import app.morrow.ui.components.skySceneState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class MorrowServices(
    val repository: AlarmRepository,
    val coordinator: AlarmCoordinator,
    val themeClock: ThemeClock,
    val settings: MorrowSettings,
    val permissions: PermissionGate,
)

/** Screens this phase doesn't build yet. The host shows its debug tools for them. */
class PendingScreens(
    val newAlarm: () -> Unit,
    val editAlarm: (id: Long) -> Unit,
    val trySunrise: () -> Unit,
    val settings: () -> Unit,
)

/**
 * The app: first run until it's finished, then Home.
 * [onSystemBarsChange] says whether the status bar icons should be light, to stay readable over the sky.
 */
@Composable
fun MorrowApp(services: MorrowServices, pending: PendingScreens, onSystemBarsChange: (lightIcons: Boolean) -> Unit) {
    val theme by services.themeClock.theme.collectAsState()
    val firstRunDone by services.settings.firstRunDone.collectAsState(initial = null)
    MorrowTheme(theme) {
        Box(Modifier.fillMaxSize().background(MorrowTheme.colors.bg)) {
            when (firstRunDone) {
                null -> Unit // Settings are still loading; avoid flashing the wrong screen.
                false -> FirstRun(services, theme, onSystemBarsChange)
                true -> Home(services, theme, pending, onSystemBarsChange)
            }
        }
    }
}

@Composable
private fun FirstRun(services: MorrowServices, theme: ThemeKind, onSystemBarsChange: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val missing by services.permissions.missing.collectAsStateWithLifecycle()
    var setup by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
    if (!setup) {
        WelcomeScreen(onContinue = { setup = true })
    } else {
        SetupScreen(
            missing = missing,
            onAllow = services.permissions::request,
            onContinue = { scope.launch { services.settings.markFirstRunDone() } },
        )
    }
}

@Composable
private fun Home(services: MorrowServices, theme: ThemeKind, pending: PendingScreens, onSystemBarsChange: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val alarms by services.repository.alarms.collectAsStateWithLifecycle<List<Alarm>?>(initialValue = null)
    val missing by services.permissions.missing.collectAsStateWithLifecycle()
    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    val zone = TimeZone.currentSystemDefault()
    val minute = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }.toFloat()
    val lightIcons = !skySceneState(minute, theme).sky.isLightSky()
    LaunchedEffect(lightIcons) { onSystemBarsChange(lightIcons) }

    val loaded = alarms ?: return
    HomeScreen(
        model = homeModel(loaded, now, zone, missing),
        theme = theme,
        minuteOfDay = minute,
        actions = HomeActions(
            onToggle = { id, enabled -> scope.launch { services.coordinator.setEnabled(id, enabled) } },
            onOpenAlarm = pending.editAlarm,
            onNewAlarm = pending.newAlarm,
            onTrySunrise = pending.trySunrise,
            onSettings = pending.settings,
            onFixPermissions = { missing.firstOrNull { it.required }?.let(services.permissions::request) },
        ),
    )
}

/** The current time, then again at the start of every minute. Collect it only while the screen is visible. */
internal fun minuteTicks(clock: Clock = Clock.System): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        delay(60_000 - now.toEpochMilliseconds() % 60_000)
    }
}
