package app.morrow.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.morrow.domain.Alarm
import app.morrow.domain.Difficulty
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.RingSound
import app.morrow.domain.WakeSound
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Adding temporary alarms keeps every alarm as it was, and none of them temporary. */
@RunWith(AndroidJUnit4::class)
class TemporaryMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("temporary-migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun alarmsStayAndNoneIsTemporary() = runTest {
        writeVersion12()

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val alarms = AlarmRepository(database.alarmDao())
            val standUp = Alarm(
                2, 9, 0, "Stand-up", DayOfWeek.entries.take(5).toSet(), listOf(GameStep(MissionType.TYPE, 4)), enabled = true,
                sound = RingSound.Bundled(WakeSound.WIND_CHIMES),
                difficulty = Difficulty.TOUGH,
                snoozeMinutes = 10,
                snoozeLimit = 2,
                description = "Laptop is in the bag",
            )
            assertEquals(listOf(standUp), alarms.getAll())

            val nap = Alarm(0, 14, 20, "Nap", emptySet(), listOf(GameStep(MissionType.SUMS, 3)), enabled = true, temporary = true)
            val id = alarms.save(nap)
            assertEquals(nap.copy(id = id), alarms.get(id))
        } finally {
            database.close()
        }
    }

    /** A database as version 12 of the app left it, from shared/schemas/.../12.json. */
    private fun writeVersion12() {
        file.parentFile?.mkdirs()
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `days` INTEGER NOT NULL, `games` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, `time_zone` TEXT, `zone_label` TEXT, `sound` TEXT, `difficulty` TEXT, `snooze_minutes` INTEGER NOT NULL DEFAULT 5, `snooze_limit` INTEGER NOT NULL DEFAULT 3, `description` TEXT)",
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
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `timers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration_ms` INTEGER NOT NULL, " +
                    "`ends_at` INTEGER, `remaining_ms` INTEGER, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `own_sounds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, `added_at` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'aaa5f0e326c029e9d6c2566d5ab45f87')")
            connection.execSQL(
                "INSERT INTO alarms (id, hour, minute, label, days, games, enabled, sound, difficulty, snooze_minutes, snooze_limit, description) VALUES (2, 9, 0, 'Stand-up', 31, 'TYPE:4', 1, 'WIND_CHIMES', 'TOUGH', 10, 2, 'Laptop is in the bag')",
            )
            connection.execSQL("PRAGMA user_version = 12")
        } finally {
            connection.close()
        }
    }
}
