package app.morrow.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.SUNDAY
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Updating to zoned alarms keeps every alarm on the phone's clock, and every saved city. */
@RunWith(AndroidJUnit4::class)
class ZonesMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("zones-migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun alarmsAndCitiesStayAndAlarmsFollowThePhone() = runTest {
        writeVersion3(
            alarms = listOf(
                "(1, 6, 30, 'Work days', 31, 'SUMS:3,BREATHE:2', 1)",
                "(4, 9, 0, 'Slow Sunday', 64, 'BREATHE:3', 0)",
            ),
            cities = listOf(
                "(2643743, 'London', 'England', 'United Kingdom', 51.5085, -0.1257, 'Europe/London', 8961989, 1, 0)",
                "(5128581, 'New York City', 'New York', 'United States', 40.7143, -74.006, 'America/New_York', 8804190, 0, 1)",
            ),
        )

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val alarms = AlarmRepository(database.alarmDao())
            val workDays = Alarm(1, 6, 30, "Work days", DayOfWeek.entries.take(5).toSet(), listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2)), enabled = true)
            assertEquals(
                listOf(workDays, Alarm(4, 9, 0, "Slow Sunday", setOf(SUNDAY), listOf(GameStep(MissionType.BREATHE, 3)), enabled = false)),
                alarms.getAll().sortedBy { it.id },
            )
            assertEquals(
                listOf(
                    SavedCityEntity(2643743, "London", "England", "United Kingdom", 51.5085, -0.1257, "Europe/London", 8961989, isHome = true, position = 0),
                    SavedCityEntity(5128581, "New York City", "New York", "United States", 40.7143, -74.006, "America/New_York", 8804190, isHome = false, position = 1),
                ),
                database.savedCityDao().getAll(),
            )

            val meeting = workDays.copy(timeZone = "America/New_York", zoneLabel = "New York")
            alarms.save(meeting)
            assertEquals(meeting, alarms.get(1))
        } finally {
            database.close()
        }
    }

    /** A database as version 3 of the app left it, from shared/schemas/.../3.json. */
    private fun writeVersion3(alarms: List<String>, cities: List<String>) {
        file.parentFile?.mkdirs()
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `days` INTEGER NOT NULL, `games` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_cities` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `region` TEXT, " +
                    "`country` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `time_zone` TEXT NOT NULL, " +
                    "`population` INTEGER NOT NULL, `is_home` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3f9756dbe6aa51eac87a67c46ef040b2')")
            alarms.forEach { connection.execSQL("INSERT INTO alarms (id, hour, minute, label, days, games, enabled) VALUES $it") }
            cities.forEach {
                connection.execSQL(
                    "INSERT INTO saved_cities (id, name, region, country, latitude, longitude, time_zone, population, is_home, position) VALUES $it",
                )
            }
            connection.execSQL("PRAGMA user_version = 3")
        } finally {
            connection.close()
        }
    }
}
