package app.morrow

import app.morrow.alarm.AlarmKitBridge
import app.morrow.alarm.IosAlarmScheduler
import app.morrow.alarm.IosAlarmSound
import app.morrow.alarm.IosTimerNotifier
import app.morrow.alarm.IosTimerScheduler
import app.morrow.alarms.AlarmCoordinator
import app.morrow.alarms.alarmIdOf
import app.morrow.data.AlarmRepository
import app.morrow.data.buildMorrowDatabase
import app.morrow.data.morrowDatabaseBuilder
import app.morrow.data.morrowSettings
import app.morrow.domain.WakeSound
import app.morrow.missions.IosCodeScanner
import app.morrow.missions.IosMotionSensor
import app.morrow.permissions.IosPermissionGate
import app.morrow.places.CityRepository
import app.morrow.places.CitySearch
import app.morrow.places.bundledCityCatalog
import app.morrow.platform.IosHttpClient
import app.morrow.platform.IosLocationProvider
import app.morrow.platform.IosSpeaker
import app.morrow.theme.ThemeClock
import app.morrow.timers.TimerCoordinator
import app.morrow.timers.SystemTimers
import app.morrow.timers.TimerRepository
import app.morrow.timers.isTimerAlarmKitId
import app.morrow.timers.timerIdOf
import app.morrow.ui.MorrowServices
import app.morrow.ui.mission.MissionServices
import app.morrow.weather.OpenMeteoGeocoder
import app.morrow.weather.OpenMeteoSource
import app.morrow.weather.RefreshReason
import app.morrow.weather.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSNotificationCenter
import platform.Foundation.currentLocale
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationSignificantTimeChangeNotification
import kotlin.time.Clock

/** What lives as long as the app, like Android's MorrowApp. The Swift app calls [start] at launch, before any screen. */
object IosApp {

    private val scope = MainScope()
    private val syncing = Mutex()
    private lateinit var alarmKit: AlarmKitBridge
    private var sound = WakeSound.BIRDSONG

    /** False when alarms may have been saved without reaching AlarmKit, because it wasn't allowed yet. */
    private var inSync = false

    lateinit var services: MorrowServices
        private set

    private val wakingAlarm = MutableStateFlow<Long?>(null)

    /** The alarm whose mission to run, after its "Start mission" button opened the app. */
    val waking: StateFlow<Long?> = wakingAlarm.asStateFlow()

    private val wakingTimersState = MutableStateFlow<List<Long>>(emptyList())

    /** The timers whose alerts' "Open" button opened the app, for their done screen. */
    val wakingTimers: StateFlow<List<Long>> = wakingTimersState.asStateFlow()

    fun start(alarmKit: AlarmKitBridge) {
        if (::alarmKit.isInitialized) return
        this.alarmKit = alarmKit
        val settings = morrowSettings()
        val database = buildMorrowDatabase(morrowDatabaseBuilder())
        val repository = AlarmRepository(database.alarmDao())
        val coordinator = AlarmCoordinator(repository, IosAlarmScheduler(alarmKit) { sound })
        val themeClock = ThemeClock(scope)
        val location = IosLocationProvider()
        val permissions = IosPermissionGate(alarmKit, location)
        // Nothing may use these while weather is off.
        val http = IosHttpClient(appVersion())
        val scanner = IosCodeScanner()
        val missions = MissionServices(settings, IosMotionSensor(), scanner)
        val catalog = bundledCityCatalog()
        val cities = CityRepository(database.savedCityDao(), catalog) { TimeZone.currentSystemDefault().id }
        val citySearch = CitySearch(catalog, OpenMeteoGeocoder(http), database.citySearchCacheDao(), settings.weatherEnabled, Clock.System)
        val weather = WeatherRepository(
            settings, cities, catalog, location, OpenMeteoSource(http, Clock.System), database.weatherCacheDao(), Clock.System,
            phoneZone = { TimeZone.currentSystemDefault().id },
            scope = scope,
        )
        val country = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String
        val timers = TimerCoordinator(TimerRepository(database.timerDao()), IosTimerScheduler(alarmKit), IosTimerNotifier)
        val speaker = IosSpeaker()
        services = MorrowServices(
            repository, coordinator, themeClock, settings, permissions, IosAlarmSound(), missions, cities, catalog, citySearch, weather,
            phoneCountry = country.orEmpty(),
            speaker = speaker,
            timers = timers,
        )
        // At launch, trust that each alarm reached AlarmKit when it was saved.
        inSync = alarmKit.authorized

        settings.pinnedTheme.onEach(themeClock::pin).launchIn(scope)
        settings.voiceId.onEach(speaker::setVoice).launchIn(scope)
        // Catches a timer stopped on its alert while Morrow is open.
        alarmKit.watch { syncTimers() }
        settings.alarmSetup.map { it.sound }.distinctUntilChanged()
            .onEach { sound = it }
            .drop(1)
            // AlarmKit keeps the sound each alarm was scheduled with, so hand every alarm over again.
            .onEach { syncAlarms(again = true) }
            .launchIn(scope)
        permissions.missing.onEach { syncAlarms() }.launchIn(scope)
        scope.launch { cities.ensureSeeded() }

        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            themeClock.refresh()
            weather.refreshSoon(RefreshReason.START)
            permissions.refresh()
            scanner.refresh()
            syncAlarms()
            syncTimers()
        }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            themeClock.pause()
            // The last time before an alarm is a rough bedtime.
            scope.launch { settings.markClosed(Clock.System.now()) }
        }
        center.addObserverForName(UIApplicationSignificantTimeChangeNotification, null, NSOperationQueue.mainQueue) { _ ->
            themeClock.refresh()
        }
    }

    /** Called by the alarm's "Start mission" button. */
    fun wake(alarmId: Long) {
        wakingAlarm.value = alarmId
    }

    fun woken() {
        wakingAlarm.value = null
    }

    /**
     * Called by a timer alert's "Open" button, before it stops the alert, so that [syncTimers] doesn't take the timer
     * for one stopped on its alert.
     */
    fun wakeTimer(timerId: Long) {
        if (timerId !in wakingTimersState.value) wakingTimersState.value += timerId
    }

    fun timersWoken() {
        wakingTimersState.value = emptyList()
    }

    /**
     * Keeps AlarmKit in step with the stored alarms. AlarmKit rings and stops alarms without telling Morrow,
     * so this catches up with the ones that rang while it was closed.
     */
    private fun syncAlarms(again: Boolean = false) = scope.launch {
        syncing.withLock {
            sound = services.settings.alarmSetup.first().sound
            val coordinator = services.coordinator
            when {
                !alarmKit.authorized -> inSync = false
                again || !inSync -> {
                    coordinator.rescheduleAll()
                    inSync = true
                }
                else -> coordinator.catchUp(alarmKit.scheduledIds().filterNot(::isTimerAlarmKitId).map(::alarmIdOf).toSet())
            }
        }
    }

    /**
     * Keeps the stored timers in step with AlarmKit, which rings them and lets the user stop or resume them on its own
     * views without telling Morrow. Timers opened from their alerts are left for their done screen. When the timers
     * can't be settled yet, this runs again a little later.
     */
    private fun syncTimers(): Job = scope.launch {
        if (!alarmKit.authorized) return@launch
        fun timers(ids: List<Long>) = ids.filter(::isTimerAlarmKitId).map(::timerIdOf).toSet()
        val system = SystemTimers(
            held = timers(alarmKit.scheduledIds()) + wakingTimersState.value,
            counting = timers(alarmKit.countingDownIds()),
            alerting = timers(alarmKit.alertingIds()),
            refused = timers(alarmKit.refusedIds()),
        )
        if (services.timers.catchUp(system)) {
            syncingTimersAgain?.cancel()
            syncingTimersAgain = scope.launch {
                delay(TimerCoordinator.ALERT_HANDOVER)
                syncTimers()
            }
        }
    }

    private var syncingTimersAgain: Job? = null

    private fun appVersion(): String = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
}
