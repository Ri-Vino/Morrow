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
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Alarms saved before wake-up games must still ring, each with its old mission as its only game. */
@RunWith(AndroidJUnit4::class)
class GamesMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.getDatabasePath("migration-test.db")

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(file.name)
    }

    @Test
    fun version1MissionsBecomeOneGame() = runTest {
        writeVersion1(
            "(1, 6, 30, 'Work days', 31, 'SUMS', 1)",
            "(2, 9, 0, 'Slow Sunday', 64, 'BREATHE', 1)",
            "(3, 7, 15, 'Gym', 1, 'SHAKE', 0)",
            "(7, 5, 45, 'Tea', 0, 'SCAN', 1)",
        )

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val repository = AlarmRepository(database.alarmDao())
            assertEquals(
                listOf(
                    Alarm(1, 6, 30, "Work days", DayOfWeek.entries.take(5).toSet(), listOf(GameStep(MissionType.SUMS, 3)), enabled = true),
                    Alarm(2, 9, 0, "Slow Sunday", setOf(SUNDAY), listOf(GameStep(MissionType.BREATHE, 3)), enabled = true),
                    Alarm(3, 7, 15, "Gym", setOf(MONDAY), listOf(GameStep(MissionType.SHAKE, 3)), enabled = false),
                    Alarm(7, 5, 45, "Tea", emptySet(), listOf(GameStep(MissionType.SCAN, 1)), enabled = true),
                ),
                repository.getAll().sortedBy { it.id },
            )

            // New alarms carry on after the old ids.
            val id = repository.save(Alarm(0, 8, 0, "New", emptySet(), listOf(GameStep(MissionType.SUMS, 2)), enabled = true))
            assertEquals(8, id)
        } finally {
            database.close()
        }
    }

    @Test
    fun idsOfDeletedAlarmsAreNotReused() = runTest {
        writeVersion1("(1, 6, 30, 'Work days', 31, 'SUMS', 1)", "(12, 7, 0, 'Gone', 0, 'SUMS', 1)", then = "DELETE FROM alarms WHERE id = 12")

        val database = buildMorrowDatabase(Room.databaseBuilder<MorrowDatabase>(context, file.absolutePath))
        try {
            val id = AlarmRepository(database.alarmDao()).save(Alarm(0, 8, 0, "New", emptySet(), listOf(GameStep(MissionType.SUMS, 2)), enabled = true))
            assertEquals(13, id)
        } finally {
            database.close()
        }
    }

    /** A database as version 1 of the app left it, from shared/schemas/.../1.json, holding [rows], after running [then]. */
    private fun writeVersion1(vararg rows: String, then: String? = null) {
        file.parentFile?.mkdirs()
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `days` INTEGER NOT NULL, `mission` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL)",
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '49a48e8c78d22015b4214ac8ec0f8882')")
            rows.forEach { connection.execSQL("INSERT INTO alarms (id, hour, minute, label, days, mission, enabled) VALUES $it") }
            then?.let(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }
}
