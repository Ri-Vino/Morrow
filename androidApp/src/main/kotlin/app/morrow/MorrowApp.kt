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
import app.morrow.places.bundledCityCatalog
import app.morrow.theme.ThemeClock
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
