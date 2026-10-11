package app.morrow.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Updating to the weather caches keeps every alarm, zoned ones included, and every saved city; the caches start empty. */
@RunWith(AndroidJUnit4::class)
class WeatherMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("weather-migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun alarmsAndCitiesStayAndTheCachesStartEmpty() = runTest {
        writeVersion4(
            alarms = listOf(
                "(1, 6, 30, 'Work days', 31, 'SUMS:3,BREATHE:2', 1, NULL, NULL)",
                "(2, 9, 0, 'Stand-up', 31, 'TYPE:4', 1, 'America/New_York', 'New York')",
            ),
            cities = listOf(
                "(2643743, 'London', 'England', 'United Kingdom', 51.5085, -0.1257, 'Europe/London', 8961989, 1, 0)",
                "(3133880, 'Tromsø', 'Troms', 'Norway', 69.6496, 18.957, 'Europe/Oslo', 41915, 0, 1)",
            ),
        )

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val workDays = DayOfWeek.entries.take(5).toSet()
            assertEquals(
                listOf(
                    Alarm(1, 6, 30, "Work days", workDays, listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2)), enabled = true),
                    Alarm(2, 9, 0, "Stand-up", workDays, listOf(GameStep(MissionType.TYPE, 4)), enabled = true, timeZone = "America/New_York", zoneLabel = "New York"),
                ),
                AlarmRepository(database.alarmDao()).getAll().sortedBy { it.id },
            )
            assertEquals(
                listOf(
                    SavedCityEntity(2643743, "London", "England", "United Kingdom", 51.5085, -0.1257, "Europe/London", 8961989, isHome = true, position = 0),
                    SavedCityEntity(3133880, "Tromsø", "Troms", "Norway", 69.6496, 18.957, "Europe/Oslo", 41915, isHome = false, position = 1),
                ),
                database.savedCityDao().getAll(),
            )

            val weather = database.weatherCacheDao()
            assertEquals(emptyList(), weather.getAll())
            val london = WeatherCacheEntity("51.51,-0.13", "{}", 1_000)
            weather.upsert(listOf(london, WeatherCacheEntity("69.65,18.96", "{}", 2_000)))
            weather.deleteAllExcept(listOf("51.51,-0.13"))
            assertEquals(listOf(london), weather.observeAll().first())

            val search = database.citySearchCacheDao()
            assertNull(search.get("hebden"))
            search.upsert(CitySearchCacheEntity("hebden", "[]", 1_000))
            search.upsert(CitySearchCacheEntity("lon", "[]", 5_000))
            search.deleteOlderThan(2_000)
            assertNull(search.get("hebden"))
            assertEquals(CitySearchCacheEntity("lon", "[]", 5_000), search.get("lon"))
        } finally {
            database.close()
        }
    }

    /** A database as version 4 of the app left it, from shared/schemas/.../4.json. */
    private fun writeVersion4(alarms: List<String>, cities: List<String>) {
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
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '58ae22270a3b18cc4d98ad1064fc908e')")
            alarms.forEach {
                connection.execSQL("INSERT INTO alarms (id, hour, minute, label, days, games, enabled, time_zone, zone_label) VALUES $it")
            }
            cities.forEach {
                connection.execSQL(
                    "INSERT INTO saved_cities (id, name, region, country, latitude, longitude, time_zone, population, is_home, position) VALUES $it",
                )
            }
            connection.execSQL("PRAGMA user_version = 4")
        } finally {
            connection.close()
        }
    }
}
