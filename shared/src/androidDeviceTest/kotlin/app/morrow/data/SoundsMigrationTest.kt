package app.morrow.data

import app.morrow.domain.RingSound
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.WakeSound
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Updating to per-alarm sounds keeps every alarm, each ringing with the sound chosen in Settings until given its own. */
@RunWith(AndroidJUnit4::class)
class SoundsMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("sounds-migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun alarmsStayOnTheDefaultSoundAndKeepTheirOwn() = runTest {
        writeVersion6()

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val alarms = AlarmRepository(database.alarmDao())
            val standUp = Alarm(2, 9, 0, "Stand-up", DayOfWeek.entries.take(5).toSet(), listOf(GameStep(MissionType.TYPE, 4)), enabled = true)
            assertEquals(listOf(standUp), alarms.getAll())

            val chimes = standUp.copy(sound = RingSound.Bundled(WakeSound.WIND_CHIMES))
            alarms.save(chimes)
            assertEquals(chimes, alarms.get(2))
            alarms.save(standUp)
            assertEquals(standUp, alarms.get(2))
        } finally {
            database.close()
        }
    }

    /** A database as version 6 of the app left it, from shared/schemas/.../6.json. */
    private fun writeVersion6() {
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
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `timers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration_ms` INTEGER NOT NULL, " +
                    "`ends_at` INTEGER, `remaining_ms` INTEGER, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6cb95cfd979336757a9752c95ed29c24')")
            connection.execSQL(
                "INSERT INTO alarms (id, hour, minute, label, days, games, enabled) VALUES (2, 9, 0, 'Stand-up', 31, 'TYPE:4', 1)",
            )
            connection.execSQL("PRAGMA user_version = 6")
        } finally {
            connection.close()
        }
    }
}
