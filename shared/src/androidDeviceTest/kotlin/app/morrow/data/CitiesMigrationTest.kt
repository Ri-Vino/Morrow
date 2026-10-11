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

/** Updating to the World tab keeps every alarm and starts with no saved cities. */
@RunWith(AndroidJUnit4::class)
class CitiesMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("cities-migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun alarmsStayAndSavedCitiesStartEmpty() = runTest {
        writeVersion2(
            "(1, 6, 30, 'Work days', 31, 'SUMS:3,BREATHE:2', 1)",
            "(4, 9, 0, 'Slow Sunday', 64, 'BREATHE:3', 0)",
        )

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            assertEquals(
                listOf(
                    Alarm(1, 6, 30, "Work days", DayOfWeek.entries.take(5).toSet(), listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2)), enabled = true),
                    Alarm(4, 9, 0, "Slow Sunday", setOf(SUNDAY), listOf(GameStep(MissionType.BREATHE, 3)), enabled = false),
                ),
                AlarmRepository(database.alarmDao()).getAll().sortedBy { it.id },
            )
            val cities = database.savedCityDao()
            assertEquals(emptyList(), cities.getAll())

            val london = SavedCityEntity(2643743, "London", "England", "United Kingdom", 51.5085, -0.1257, "Europe/London", 8961989, isHome = true, position = 0)
            val singapore = SavedCityEntity(1880252, "Singapore", null, "Singapore", 1.2897, 103.8501, "Asia/Singapore", 5638700, isHome = false, position = 1)
            cities.upsert(listOf(london, singapore))
            assertEquals(listOf(london, singapore), cities.getAll())
        } finally {
            database.close()
        }
    }

    /** A database as version 2 of the app left it, from shared/schemas/.../2.json, holding [rows]. */
    private fun writeVersion2(vararg rows: String) {
        file.parentFile?.mkdirs()
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `days` INTEGER NOT NULL, `games` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a2401219a07b6968479a7ca0651d1b0d')")
            rows.forEach { connection.execSQL("INSERT INTO alarms (id, hour, minute, label, days, games, enabled) VALUES $it") }
            connection.execSQL("PRAGMA user_version = 2")
        } finally {
            connection.close()
        }
    }
}
