package app.morrow.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {

    private val database = buildMorrowDatabase(
        Room.inMemoryDatabaseBuilder<MorrowDatabase>(InstrumentationRegistry.getInstrumentation().targetContext),
    )
    private val repository = AlarmRepository(database.alarmDao())

    @After
    fun close() = database.close()

    @Test
    fun roundTripThroughSqlite() = runTest {
        val games = listOf(GameStep(MissionType.BREATHE, 2), GameStep(MissionType.SUMS, 4))
        val weekend = Alarm(0, 9, 0, "Slow Sunday", setOf(SATURDAY, SUNDAY), games, enabled = true)

        val id = repository.save(weekend)
        assertEquals(listOf(weekend.copy(id = id)), repository.alarms.first())

        repository.save(weekend.copy(id = id, hour = 10))
        assertEquals(10, repository.get(id)?.hour)

        repository.setEnabled(id, false)
        assertEquals(false, repository.get(id)?.enabled)

        repository.delete(id)
        assertNull(repository.get(id))
    }
}
