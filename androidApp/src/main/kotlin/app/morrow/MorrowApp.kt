package app.morrow

import android.app.Application
import android.content.Context
import app.morrow.alarm.AndroidAlarmScheduler
import app.morrow.alarms.AlarmCoordinator
import app.morrow.data.AlarmRepository
import app.morrow.data.buildMorrowDatabase
import app.morrow.data.morrowDatabaseBuilder
import app.morrow.data.morrowSettings
import app.morrow.places.CityRepository
import app.morrow.places.CitySearch
import app.morrow.places.bundledCityCatalog
import app.morrow.platform.AndroidHttpClient
import app.morrow.platform.AndroidLocationProvider
import app.morrow.theme.ThemeClock
import app.morrow.weather.OpenMeteoGeocoder
import app.morrow.weather.OpenMeteoSource
import app.morrow.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class MorrowApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database by lazy { buildMorrowDatabase(morrowDatabaseBuilder(this)) }
    val repository by lazy { AlarmRepository(database.alarmDao()) }
    val coordinator by lazy { AlarmCoordinator(repository, AndroidAlarmScheduler(this)) }
    val themeClock by lazy { ThemeClock(appScope) }
    val settings by lazy { morrowSettings(this) }
    val catalog by lazy { bundledCityCatalog() }
    val cities by lazy { CityRepository(database.savedCityDao(), catalog) { TimeZone.currentSystemDefault().id } }

    // Nothing may use these while weather is off.
    val http by lazy { AndroidHttpClient(BuildConfig.VERSION_NAME) }
    val location by lazy { AndroidLocationProvider(this) }
    val weatherSource by lazy { OpenMeteoSource(http, Clock.System) }
    val geocoder by lazy { OpenMeteoGeocoder(http) }
    val citySearch by lazy { CitySearch(catalog, geocoder, database.citySearchCacheDao(), settings.weatherEnabled, Clock.System) }
    val weather by lazy {
        WeatherRepository(
            settings, cities, catalog, location, weatherSource, database.weatherCacheDao(), Clock.System,
            phoneZone = { TimeZone.currentSystemDefault().id },
            scope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { settings.pinnedTheme.collect(themeClock::pin) }
        appScope.launch { cities.ensureSeeded() }
    }

    /** Remembers when the user left the app; the last time before an alarm is a rough bedtime. */
    fun markClosed() {
        appScope.launch { settings.markClosed(Clock.System.now()) }
    }
}

val Context.morrow: MorrowApp get() = applicationContext as MorrowApp
