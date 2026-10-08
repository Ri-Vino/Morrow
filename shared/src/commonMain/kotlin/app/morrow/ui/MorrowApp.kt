package app.morrow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.alarms.AlarmCoordinator
import app.morrow.data.AlarmRepository
import app.morrow.data.MorrowSettings
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.domain.nextAlarm
import app.morrow.platform.AlarmSound
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeClock
import app.morrow.theme.ThemeKind
import app.morrow.theme.isLightSky
import app.morrow.ui.edit.EditActions
import app.morrow.ui.edit.EditScreen
import app.morrow.ui.edit.KettleCodeScreen
import app.morrow.ui.edit.editModel
import app.morrow.ui.firstrun.SetupScreen
import app.morrow.ui.firstrun.WelcomeScreen
import app.morrow.ui.home.HomeActions
import app.morrow.ui.home.HomeScreen
import app.morrow.ui.home.homeModel
import app.morrow.ui.components.skySceneState
import app.morrow.ui.mission.MissionRoute
import app.morrow.ui.mission.MissionServices
import app.morrow.ui.morning.MorningRoute
import app.morrow.ui.ring.RingPreview
import app.morrow.ui.ring.RingScreen
import app.morrow.ui.ring.ringModel
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
    val alarmSound: AlarmSound,
    val missions: MissionServices,
)

/** Screens not built yet. The host shows its debug tools for them. */
class PendingScreens(
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
                    // Keeps Home's scroll and Edit's draft while a preview covers them.
                    val screens = rememberSaveableStateHolder()
                    var editing by rememberSaveable { mutableStateOf<String?>(null) }
                    var previewing by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf(null) }
                    val target = editing
                    val preview = previewing
                    when {
                        preview != null -> RingPreviewRoute(services, preview, onSystemBarsChange, onExit = { previewing = null })
                        target == null -> screens.SaveableStateProvider(HOME) {
                            Home(
                                services, theme, pending, onSystemBarsChange,
                                onEdit = { editing = it?.toString() ?: NEW_ALARM },
                                onPreview = { previewing = it },
                            )
                        }
                        else -> screens.SaveableStateProvider(EDIT) {
                            EditRoute(
                                services, theme, target.toLongOrNull(), onSystemBarsChange,
                                onPreview = { previewing = it },
                                onDone = {
                                    editing = null
                                    screens.removeState(EDIT)
                                },
                            )
                        }
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
    onPreview: (Alarm) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val alarms by services.repository.alarms.collectAsStateWithLifecycle<List<Alarm>?>(initialValue = null)
    val missing by services.permissions.missing.collectAsStateWithLifecycle()
    val intention by services.settings.intention.collectAsStateWithLifecycle(initialValue = null)
    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    val zone = TimeZone.currentSystemDefault()
    val minute = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }.toFloat()
    val lightIcons = !skySceneState(minute, theme).sky.isLightSky()
    LaunchedEffect(lightIcons) { onSystemBarsChange(lightIcons) }

    val loaded = alarms ?: return
    HomeScreen(
        model = homeModel(loaded, now, zone, missing, intention),
        theme = theme,
        minuteOfDay = minute,
        actions = HomeActions(
            onToggle = { id, enabled -> scope.launch { services.coordinator.setEnabled(id, enabled) } },
            onOpenAlarm = { onEdit(it) },
            onNewAlarm = { onEdit(null) },
            // The alarm the sleep card is about.
            onTrySunrise = { onPreview(loaded.nextAlarm(Clock.System.now(), zone)?.alarm ?: loaded.firstOrNull() ?: newAlarm()) },
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
    onSystemBarsChange: (Boolean) -> Unit,
    onPreview: (Alarm) -> Unit,
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
    val setup by services.settings.missionSetup.collectAsStateWithLifecycle(initialValue = null)
    var pickingCode by rememberSaveable { mutableStateOf(false) }

    val current = draft ?: return
    val missions = setup ?: return
    if (pickingCode) {
        KettleCodeScreen(
            scanner = services.missions.scanner,
            onSaved = { code ->
                scope.launch { services.settings.setKettleCode(code) }
                draft = current.copy(mission = MissionType.SCAN)
                pickingCode = false
            },
            onCancel = { pickingCode = false },
        )
        return
    }
    EditScreen(
        draft = current,
        model = editModel(current, nowMinute, sumsCount = missions.difficulty.sums, shakeGoal = missions.difficulty.shakes),
        actions = EditActions(
            onChange = { changed ->
                // Scan needs a code to look for, so save one the first time it's picked.
                val needsCode = changed.mission == MissionType.SCAN && current.mission != MissionType.SCAN && missions.kettleCode == null
                if (needsCode) pickingCode = true else draft = changed
            },
            onBack = onDone,
            onDelete = {
                scope.launch {
                    services.coordinator.delete(current.id)
                    onDone()
                }
            },
            onPreview = { onPreview(current) },
            onSave = {
                scope.launch {
                    services.coordinator.save(current.copy(enabled = true))
                    onDone()
                }
            },
        ),
    )
}

/** The ringing screen as it will look for [alarm], silent unless the user asks to hear it. */
@Composable
private fun RingPreviewRoute(services: MorrowServices, alarm: Alarm, onSystemBarsChange: (Boolean) -> Unit, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var soundOn by rememberSaveable { mutableStateOf(false) }
    var stage by rememberSaveable { mutableStateOf(PreviewStage.RINGING) }
    var wokeAt by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(stage) { onSystemBarsChange(stage != PreviewStage.MORNING) }
    PlatformBackHandler(onBack = onExit)
    DisposableEffect(soundOn) {
        if (soundOn) services.alarmSound.play()
        onDispose { services.alarmSound.stop() }
    }
    // Leaving the app, or a real alarm taking over the screen, ends the sound for good.
    LifecycleStartEffect(Unit) { onStopOrDispose { soundOn = false } }
    when (stage) {
        PreviewStage.RINGING -> RingScreen(
            model = ringModel(alarm),
            onStartMission = { stage = PreviewStage.MISSION },
            preview = RingPreview(soundOn, onToggleSound = { soundOn = !soundOn }, onExit = onExit),
        )
        PreviewStage.MISSION -> MissionRoute(
            alarm.mission,
            services.missions,
            onDone = {
                soundOn = false
                wokeAt = Clock.System.now().toEpochMilliseconds()
                stage = PreviewStage.MORNING
            },
        )
        PreviewStage.MORNING -> MorningRoute(
            settings = services.settings,
            wokeAt = Instant.fromEpochMilliseconds(wokeAt),
            onStartDay = { intention ->
                scope.launch {
                    if (intention.isNotEmpty()) services.settings.setIntention(intention)
                    onExit()
                }
            },
        )
    }
}

private enum class PreviewStage { RINGING, MISSION, MORNING }

private const val NEW_ALARM = "new"
private const val HOME = "home"
private const val EDIT = "edit"

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
