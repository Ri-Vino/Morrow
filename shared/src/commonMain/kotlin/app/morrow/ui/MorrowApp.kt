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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.alarms.AlarmCoordinator
import app.morrow.data.AlarmRepository
import app.morrow.data.MorrowSettings
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeClock
import app.morrow.theme.ThemeKind
import app.morrow.theme.isLightSky
import app.morrow.ui.edit.EditActions
import app.morrow.ui.edit.EditScreen
import app.morrow.ui.edit.editModel
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
import kotlinx.datetime.DayOfWeek
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

/** Screens not built yet. The host shows its debug tools for them. */
class PendingScreens(
    val trySunrise: () -> Unit,
    val preview: (Alarm) -> Unit,
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
                true -> {
                    var editing by rememberSaveable { mutableStateOf<String?>(null) }
                    val target = editing
                    if (target == null) {
                        Home(services, theme, pending, onSystemBarsChange, onEdit = { editing = it?.toString() ?: NEW_ALARM })
                    } else {
                        EditRoute(services, theme, target.toLongOrNull(), pending, onSystemBarsChange, onDone = { editing = null })
                    }
                }
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
private fun Home(
    services: MorrowServices,
    theme: ThemeKind,
    pending: PendingScreens,
    onSystemBarsChange: (Boolean) -> Unit,
    /** Opens Edit for an alarm, or for a new one when the id is null. */
    onEdit: (Long?) -> Unit,
) {
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
            onOpenAlarm = { onEdit(it) },
            onNewAlarm = { onEdit(null) },
            onTrySunrise = pending.trySunrise,
            onSettings = pending.settings,
            onFixPermissions = { missing.firstOrNull { it.required }?.let(services.permissions::request) },
        ),
    )
}

@Composable
private fun EditRoute(
    services: MorrowServices,
    theme: ThemeKind,
    alarmId: Long?,
    pending: PendingScreens,
    onSystemBarsChange: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf(null) }
    LaunchedEffect(alarmId) {
        if (draft == null) draft = alarmId?.let { services.repository.get(it) } ?: newAlarm()
    }
    LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
    PlatformBackHandler(onBack = onDone)
    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    val nowMinute = now.toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour * 60 + it.minute }

    val current = draft ?: return
    EditScreen(
        draft = current,
        model = editModel(current, nowMinute),
        actions = EditActions(
            onChange = { draft = it },
            onBack = onDone,
            onDelete = {
                scope.launch {
                    services.coordinator.delete(current.id)
                    onDone()
                }
            },
            onPreview = { pending.preview(current) },
            onSave = {
                scope.launch {
                    services.coordinator.save(current.copy(enabled = true))
                    onDone()
                }
            },
        ),
    )
}

private const val NEW_ALARM = "new"

private fun newAlarm() = Alarm(
    id = 0,
    hour = 7,
    minute = 0,
    label = "Alarm",
    days = DayOfWeek.entries.take(5).toSet(),
    mission = MissionType.SUMS,
    enabled = true,
)

/** Keeps an unsaved draft through rotation and process death. */
private val AlarmSaver = Saver<Alarm?, List<Any>>(
    save = { alarm ->
        alarm?.let {
            listOf(it.id, it.hour, it.minute, it.label, it.days.joinToString(",") { day -> day.name }, it.mission.name, it.enabled)
        }
    },
    restore = { saved ->
        Alarm(
            id = saved[0] as Long,
            hour = saved[1] as Int,
            minute = saved[2] as Int,
            label = saved[3] as String,
            days = (saved[4] as String).split(",").filter(String::isNotEmpty).map(DayOfWeek::valueOf).toSet(),
            mission = MissionType.valueOf(saved[5] as String),
            enabled = saved[6] as Boolean,
        )
    },
)

/** The current time, then again at the start of every minute. Collect it only while the screen is visible. */
internal fun minuteTicks(clock: Clock = Clock.System): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        delay(60_000 - now.toEpochMilliseconds() % 60_000)
    }
}
