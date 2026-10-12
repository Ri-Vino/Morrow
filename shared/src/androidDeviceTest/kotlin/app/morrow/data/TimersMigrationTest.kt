package app.morrow.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.timers.Timer
import app.morrow.timers.TimerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Updating to timers keeps every alarm, saved city and cached forecast; the timers start empty and keep both states. */
@RunWith(AndroidJUnit4::class)
class TimersMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("timers-migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun everythingStaysAndTimersRoundTrip() = runTest {
        writeVersion5()

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val workDays = DayOfWeek.entries.take(5).toSet()
            assertEquals(
                listOf(Alarm(2, 9, 0, "Stand-up", workDays, listOf(GameStep(MissionType.TYPE, 4)), enabled = true, timeZone = "America/New_York", zoneLabel = "New York")),
                AlarmRepository(database.alarmDao()).getAll(),
            )
            assertEquals(listOf("London"), database.savedCityDao().getAll().map { it.name })
            assertEquals(listOf(WeatherCacheEntity("51.51,-0.13", "{}", 1_000)), database.weatherCacheDao().getAll())

            val timers = TimerRepository(database.timerDao())
            assertEquals(emptyList(), timers.timers.first())
            val noon = Instant.fromEpochMilliseconds(1_791_806_400_000)
            val pasta = Timer(0, 10.minutes, noon + 10.minutes, null, "Take the pasta off the hob", noon)
            val laundry = Timer(0, 45.minutes, null, 30.minutes, "", noon + 1.minutes)
            val pastaId = timers.save(pasta)
            val laundryId = timers.save(laundry)
            assertEquals(listOf(pasta.copy(id = pastaId), laundry.copy(id = laundryId)), timers.getAll())

            timers.delete(pastaId)
            assertNull(timers.get(pastaId))
            // Ids aren't reused, so a stale notification or alarm can never point at a newer timer.
            val next = timers.save(pasta)
            assertEquals(laundryId + 1, next)
        } finally {
            database.close()
        }
    }

    /** A database as version 5 of the app left it, from shared/schemas/.../5.json. */
    private fun writeVersion5() {
        file.parentFile?.mkdirs()
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `days` INTEGER NOT NULL, `games` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, `time_zone` TEXT, `zone_label` TEXT)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_cities` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `region` TEXT, " +
                    "`country` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `time_zone` TEXT NOT NULL, " +
                    "`population` INTEGER NOT NULL, `is_home` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `weather_cache` (`place_key` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                    "`fetched_at` INTEGER NOT NULL, PRIMARY KEY(`place_key`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `city_search_cache` (`query` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                    "`fetched_at` INTEGER NOT NULL, PRIMARY KEY(`query`))",
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6035c8fddbafdf509e51e229c40b3271')")
            connection.execSQL(
                "INSERT INTO alarms (id, hour, minute, label, days, games, enabled, time_zone, zone_label) " +
                    "VALUES (2, 9, 0, 'Stand-up', 31, 'TYPE:4', 1, 'America/New_York', 'New York')",
            )
            connection.execSQL(
                "INSERT INTO saved_cities (id, name, region, country, latitude, longitude, time_zone, population, is_home, position) " +
                    "VALUES (2643743, 'London', 'England', 'United Kingdom', 51.5085, -0.1257, 'Europe/London', 8961989, 1, 0)",
            )
            connection.execSQL("INSERT INTO weather_cache (place_key, json, fetched_at) VALUES ('51.51,-0.13', '{}', 1000)")
            connection.execSQL("PRAGMA user_version = 5")
        } finally {
            connection.close()
        }
    }
}
