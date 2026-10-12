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
import androidx.compose.runtime.produceState
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
import app.morrow.data.AlarmSetup
import app.morrow.data.MorrowSettings
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.MissionType
import app.morrow.domain.decodeGames
import app.morrow.domain.encodeGames
import app.morrow.domain.nextAlarm
import app.morrow.domain.nextRing
import app.morrow.places.City
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.places.CitySearch
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AlarmSound
import app.morrow.platform.NoSpeaker
import app.morrow.platform.PermissionGate
import app.morrow.platform.Speaker
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeClock
import app.morrow.theme.ThemeKind
import app.morrow.theme.isLightSky
import app.morrow.timers.TimerCoordinator
import app.morrow.ui.edit.EditActions
import app.morrow.ui.edit.EditScreen
import app.morrow.ui.edit.KettleCodeScreen
import app.morrow.ui.edit.ZoneSheetData
import app.morrow.ui.edit.editModel
import app.morrow.ui.edit.phoneCityName
import app.morrow.ui.edit.searchChoices
import app.morrow.ui.edit.zoneChoices
import app.morrow.ui.edit.withGame
import app.morrow.ui.firstrun.SetupScreen
import app.morrow.ui.firstrun.WelcomeScreen
import app.morrow.ui.home.HomeActions
import app.morrow.ui.home.HomeScreen
import app.morrow.ui.home.homeModel
import app.morrow.ui.home.homeWeather
import app.morrow.ui.components.skySceneState
import app.morrow.ui.mission.MissionServices
import app.morrow.ui.mission.SequenceRoute
import app.morrow.ui.morning.MorningRoute
import app.morrow.ui.ring.RingPreview
import app.morrow.ui.settings.SettingsRoute
import app.morrow.ui.ring.RingScreen
import app.morrow.ui.ring.ringModel
import app.morrow.ui.tabs.Tab
import app.morrow.ui.tabs.TabHost
import app.morrow.ui.tabs.TabSaver
import app.morrow.ui.timers.TimersRoute
import app.morrow.ui.world.AddCityRoute
import app.morrow.ui.world.CityPickPurpose
import app.morrow.ui.world.WorldRoute
import app.morrow.weather.RefreshReason
import app.morrow.weather.WeatherRepository
import app.morrow.weather.WeatherState
import app.morrow.weather.defaultUnits
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
    val cities: CityRepository,
    val catalog: CityCatalog,
    val citySearch: CitySearch,
    val weather: WeatherRepository,
    /** The phone's region, like "GB", for the default temperature units. */
    val phoneCountry: String,
    val speaker: Speaker = NoSpeaker,
    val timers: TimerCoordinator,
)

/**
 * The app: first run until it's finished, then the Alarms, World and Timers tabs.
 * [onSystemBarsChange] says whether the status bar icons should be light, to stay readable over the sky.
 * [developerTools] opens the host's debug tools from Settings; pass null in release builds.
 * [waking] is an alarm whose mission to run, on iOS where the system rings the alarm; [onWoken] follows Morning.
 */
@Composable
fun MorrowApp(
    services: MorrowServices,
    developerTools: (() -> Unit)?,
    onSystemBarsChange: (lightIcons: Boolean) -> Unit,
    waking: Long? = null,
    onWoken: () -> Unit = {},
) {
    val theme by services.themeClock.theme.collectAsState()
    val firstRunDone by services.settings.firstRunDone.collectAsState(initial = null)
    MorrowTheme(theme) {
        Box(Modifier.fillMaxSize().background(MorrowTheme.colors.bg)) {
            when (firstRunDone) {
                null -> Unit // Settings are still loading; avoid flashing the wrong screen.
                false -> FirstRun(services, theme, onSystemBarsChange)
                true -> {
                    LocationGrantedRefresh(services)
                    // Keeps Home's scroll and Edit's draft while a preview covers them.
                    val screens = rememberSaveableStateHolder()
                    var editing by rememberSaveable { mutableStateOf<String?>(null) }
                    var previewing by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf(null) }
                    var inSettings by rememberSaveable { mutableStateOf(false) }
                    var addingCity by rememberSaveable { mutableStateOf(false) }
                    var pickingHome by rememberSaveable { mutableStateOf(false) }
                    var tab by rememberSaveable(stateSaver = TabSaver) { mutableStateOf(Tab.ALARMS) }
                    val target = editing
                    val preview = previewing
                    when {
                        waking != null -> WakeRoute(services, waking, onSystemBarsChange, onDone = onWoken)
                        preview != null -> RingPreviewRoute(services, preview, onSystemBarsChange, onExit = { previewing = null })
                        inSettings && pickingHome -> {
                            LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
                            AddCityRoute(
                                services.citySearch, services.cities,
                                onBack = { pickingHome = false },
                                purpose = CityPickPurpose.HOME,
                                onPicked = services.weather::refreshCitySoon,
                            )
                        }
                        inSettings -> {
                            LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
                            SettingsRoute(
                                settings = services.settings,
                                pinnedTheme = services.themeClock.pinnedTheme,
                                theme = theme,
                                permissions = services.permissions,
                                cities = services.cities,
                                here = services.weather.here,
                                phoneCountry = services.phoneCountry,
                                speaker = services.speaker,
                                developerTools = developerTools,
                                onPickHomeCity = { pickingHome = true },
                                onWeatherChanged = { services.weather.refreshSoon(RefreshReason.SETTINGS) },
                                onBack = { inSettings = false },
                            )
                        }
                        addingCity -> {
                            LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
                            AddCityRoute(services.citySearch, services.cities, onBack = { addingCity = false }, onPicked = services.weather::refreshCitySoon)
                        }
                        target == null -> {
                            if (tab != Tab.ALARMS) LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
                            var worldSheetOpen by remember { mutableStateOf(false) }
                            val world = @Composable {
                                LaunchedEffect(Unit) { services.weather.refreshSoon(RefreshReason.WORLD) }
                                WorldRoute(
                                    services.cities,
                                    onAddCity = { addingCity = true },
                                    onNewAlarm = { editing = it?.let(::newAlarmTarget) ?: NEW_ALARM },
                                    onSheetChange = { worldSheetOpen = it },
                                    weather = services.weather.forecasts,
                                    units = remember { services.settings.effectiveUnits(services.phoneCountry) },
                                )
                            }
                            TabHost(
                                tab, onSelect = { tab = it }, barHidden = tab == Tab.WORLD && worldSheetOpen, world = world,
                                timers = { screens.SaveableStateProvider(TIMERS) { TimersRoute(services.timers, services.permissions) } },
                            ) {
                                screens.SaveableStateProvider(HOME) {
                                    Home(
                                        services, theme, onSystemBarsChange,
                                        onEdit = { editing = it?.toString() ?: NEW_ALARM },
                                        onPreview = { previewing = it },
                                        onSettings = { inSettings = true },
                                        onOpenWorld = { tab = Tab.WORLD },
                                    )
                                }
                            }
                        }
                        else -> screens.SaveableStateProvider(EDIT) {
                            EditRoute(
                                services, theme, target, onSystemBarsChange,
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

/** Once location is allowed, "Where I am" weather follows straight away rather than at the next start. */
@Composable
private fun LocationGrantedRefresh(services: MorrowServices) {
    val missing by services.permissions.missing.collectAsStateWithLifecycle()
    val locationMissing = AlarmPermission.LOCATION in missing
    var wasMissing by remember { mutableStateOf(locationMissing) }
    LaunchedEffect(locationMissing) {
        if (wasMissing && !locationMissing) services.weather.refreshSoon(RefreshReason.SETTINGS)
        wasMissing = locationMissing
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
            asked = services.permissions.asked,
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
    onSystemBarsChange: (Boolean) -> Unit,
    /** Opens Edit for an alarm, or for a new one when the id is null. */
    onEdit: (Long?) -> Unit,
    onPreview: (Alarm) -> Unit,
    onSettings: () -> Unit,
    onOpenWorld: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val alarms by services.repository.alarms.collectAsStateWithLifecycle<List<Alarm>?>(initialValue = null)
    val missing by services.permissions.missing.collectAsStateWithLifecycle()
    val intention by services.settings.intention.collectAsStateWithLifecycle(initialValue = null)
    val forecasts by services.weather.forecasts.collectAsStateWithLifecycle<WeatherState?>(initialValue = null)
    val units by remember { services.settings.effectiveUnits(services.phoneCountry) }
        .collectAsStateWithLifecycle(initialValue = defaultUnits(services.phoneCountry))
    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    val zone = TimeZone.currentSystemDefault()
    val minute = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }.toFloat()
    val weather = homeWeather(forecasts, now, zone, units)
    val lightIcons = !skySceneState(minute, theme, weather?.sky).sky.isLightSky()
    LaunchedEffect(lightIcons) { onSystemBarsChange(lightIcons) }

    val loaded = alarms ?: return
    HomeScreen(
        model = homeModel(loaded, now, zone, missing, intention, weather),
        theme = theme,
        minuteOfDay = minute,
        actions = HomeActions(
            onToggle = { id, enabled -> scope.launch { services.coordinator.setEnabled(id, enabled) } },
            onOpenAlarm = { onEdit(it) },
            onNewAlarm = { onEdit(null) },
            // The alarm the sleep card is about.
            onTrySunrise = { onPreview(loaded.nextAlarm(Clock.System.now(), zone)?.alarm ?: loaded.firstOrNull() ?: newAlarm()) },
            onSettings = onSettings,
            onFixPermissions = { missing.firstOrNull { it.required }?.let(services.permissions::request) },
            onOpenWorld = onOpenWorld,
        ),
    )
}

/** Edit for [target]: an alarm's id, [NEW_ALARM], or a new alarm on a city's clock from [newAlarmTarget]. */
@Composable
private fun EditRoute(
    services: MorrowServices,
    theme: ThemeKind,
    target: String,
    onSystemBarsChange: (Boolean) -> Unit,
    onPreview: (Alarm) -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf(null) }
    LaunchedEffect(target) {
        if (draft == null) draft = target.toLongOrNull()?.let { services.repository.get(it) } ?: newAlarmFor(target)
    }
    LaunchedEffect(theme) { onSystemBarsChange(theme == ThemeKind.NIGHT) }
    PlatformBackHandler(onBack = onDone)
    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    val phoneZone = TimeZone.currentSystemDefault()
    val savedCities by services.cities.cities.collectAsStateWithLifecycle(initialValue = emptyList())
    val zoneCity by produceState<City?>(null, phoneZone) { value = services.catalog.cityForZone(phoneZone.id) }
    val setup by services.settings.missionSetup.collectAsStateWithLifecycle(initialValue = null)
    var pickingCode by rememberSaveable { mutableStateOf(false) }

    val current = draft ?: return
    val missions = setup ?: return
    if (pickingCode) {
        KettleCodeScreen(
            scanner = services.missions.scanner,
            onSaved = { code ->
                scope.launch { services.settings.setKettleCode(code) }
                draft = current.withGame(MissionType.SCAN)
                pickingCode = false
            },
            onCancel = { pickingCode = false },
        )
        return
    }
    EditScreen(
        draft = current,
        model = editModel(current, now, phoneZone),
        zones = ZoneSheetData(
            choices = zoneChoices(current, savedCities.map { it.city }, now, phoneZone, phoneCityName(savedCities, phoneZone, zoneCity)),
            find = { query -> searchChoices(services.catalog.search(query, limit = 8), Clock.System.now(), phoneZone) },
        ),
        actions = EditActions(
            onChange = { changed ->
                // Scan needs a code to look for, so save one the first time it's added.
                val needsCode = changed.games.any { it.type == MissionType.SCAN } &&
                    current.games.none { it.type == MissionType.SCAN } && missions.kettleCode == null
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
    val sound by services.settings.alarmSetup.collectAsStateWithLifecycle(initialValue = AlarmSetup.Default)
    DisposableEffect(soundOn, sound) {
        if (soundOn) services.alarmSound.play(sound.sound, sound.ramp.duration)
        onDispose { services.alarmSound.stop() }
    }
    // Leaving the app, or a real alarm taking over the screen, ends the sound for good.
    LifecycleStartEffect(Unit) { onStopOrDispose { soundOn = false } }
    val ringsAt = remember(alarm) { alarm.nextRing(Clock.System.now(), TimeZone.currentSystemDefault()) }
    when (stage) {
        PreviewStage.RINGING -> RingScreen(
            model = ringModel(alarm, ringsAt, TimeZone.currentSystemDefault()),
            onStartMission = { stage = PreviewStage.MISSION },
            preview = RingPreview(soundOn, onToggleSound = { soundOn = !soundOn }, onExit = onExit),
        )
        PreviewStage.MISSION -> SequenceRoute(
            alarm.games,
            services.missions,
            onDone = {
                soundOn = false
                wokeAt = Clock.System.now().toEpochMilliseconds()
                stage = PreviewStage.MORNING
            },
        )
        PreviewStage.MORNING -> MorningRoute(
            settings = services.settings,
            weather = services.weather,
            speaker = services.speaker,
            phoneCountry = services.phoneCountry,
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

/**
 * After the system's alarm screen, which on iOS can only stop the alarm or open the app: the alarm's games,
 * with its sound playing until the last one is done, then Morning.
 */
@Composable
private fun WakeRoute(services: MorrowServices, alarmId: Long, onSystemBarsChange: (Boolean) -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var games by rememberSaveable(alarmId) { mutableStateOf<String?>(null) }
    var wokeAt by rememberSaveable(alarmId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(alarmId) {
        if (games != null) return@LaunchedEffect
        // Yesterday's "one small thing" lasts until the next alarm, which is this one.
        services.settings.clearIntention()
        services.coordinator.onRang(alarmId)
        games = encodeGames(services.repository.get(alarmId)?.games ?: DefaultGames)
    }
    val ringing = wokeAt == null
    val setup by services.settings.alarmSetup.collectAsStateWithLifecycle(initialValue = null)
    DisposableEffect(ringing, setup) {
        val sound = setup
        if (ringing && sound != null) services.alarmSound.play(sound.sound, sound.ramp.duration)
        onDispose { services.alarmSound.stop() }
    }
    LaunchedEffect(ringing) { onSystemBarsChange(ringing) }

    val woke = wokeAt
    val chosen = games
    when {
        woke != null -> MorningRoute(
            settings = services.settings,
            weather = services.weather,
            speaker = services.speaker,
            phoneCountry = services.phoneCountry,
            wokeAt = Instant.fromEpochMilliseconds(woke),
            onStartDay = { intention ->
                scope.launch {
                    if (intention.isNotEmpty()) services.settings.setIntention(intention)
                    onDone()
                }
            },
        )
        chosen != null -> SequenceRoute(decodeGames(chosen), services.missions, onDone = { wokeAt = Clock.System.now().toEpochMilliseconds() })
    }
}

private const val NEW_ALARM = "new"
private const val HOME = "home"
private const val EDIT = "edit"
private const val TIMERS = "timers"

/**
 * What to put in `editing` to open Edit for a new alarm that keeps [city]'s clock, as the World tab's
 * "New alarm in New York time" does.
 */
internal fun newAlarmTarget(city: City): String = listOf(NEW_ALARM, city.timeZone, city.name).joinToString(ZONED_TARGET_SEPARATOR)

private const val ZONED_TARGET_SEPARATOR = "|"

/** A new alarm for an Edit target: on a city's clock when [newAlarmTarget] made it, else on the phone's. */
private fun newAlarmFor(target: String): Alarm {
    val parts = target.split(ZONED_TARGET_SEPARATOR, limit = 3)
    return if (parts.size == 3 && parts[0] == NEW_ALARM) newAlarm(timeZone = parts[1], city = parts[2]) else newAlarm()
}

private fun newAlarm(timeZone: String? = null, city: String? = null) = Alarm(
    id = 0,
    hour = 7,
    minute = 0,
    label = "Alarm",
    days = DayOfWeek.entries.take(5).toSet(),
    games = DefaultGames,
    enabled = true,
    timeZone = timeZone,
    zoneLabel = city.takeIf { timeZone != null },
)

/** Keeps an unsaved draft through rotation and process death. */
private val AlarmSaver = Saver<Alarm?, List<Any>>(
    save = { alarm ->
        alarm?.let {
            listOf(
                it.id, it.hour, it.minute, it.label, it.days.joinToString(",") { day -> day.name }, encodeGames(it.games), it.enabled,
                it.timeZone.orEmpty(), it.zoneLabel.orEmpty(),
            )
        }
    },
    restore = { saved ->
        Alarm(
            id = saved[0] as Long,
            hour = saved[1] as Int,
            minute = saved[2] as Int,
            label = saved[3] as String,
            days = (saved[4] as String).split(",").filter(String::isNotEmpty).map(DayOfWeek::valueOf).toSet(),
            games = decodeGames(saved[5] as String).ifEmpty { DefaultGames },
            enabled = saved[6] as Boolean,
            timeZone = (saved.getOrNull(7) as String?)?.ifEmpty { null },
            zoneLabel = (saved.getOrNull(8) as String?)?.ifEmpty { null },
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
