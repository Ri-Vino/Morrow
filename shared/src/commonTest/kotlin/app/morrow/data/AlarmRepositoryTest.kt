package app.morrow.data

import app.cash.turbine.test
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmRepositoryTest {

    private val dao = FakeAlarmDao()
    private val repository = AlarmRepository(dao)

    private fun alarm(hour: Int, minute: Int, days: Set<DayOfWeek> = emptySet(), id: Long = 0) =
        Alarm(id = id, hour = hour, minute = minute, label = "Gym", days = days, mission = MissionType.SCAN, enabled = true)

    @Test
    fun newAlarmGetsAnIdAndShowsUpInTheFlow() = runTest {
        repository.alarms.test {
            assertEquals(emptyList(), awaitItem())

            val id = repository.save(alarm(5, 45, days = setOf(MONDAY, WEDNESDAY, FRIDAY)))

            assertEquals(listOf(alarm(5, 45, days = setOf(MONDAY, WEDNESDAY, FRIDAY), id = id)), awaitItem())
        }
    }

    @Test
    fun savingAnExistingAlarmUpdatesIt() = runTest {
        val id = repository.save(alarm(6, 30))
        assertEquals(id, repository.save(alarm(7, 0, id = id)))
        assertEquals(alarm(7, 0, id = id), repository.get(id))
    }

    @Test
    fun repeatDaysSurviveStorage() = runTest {
        val patterns = listOf(emptySet(), setOf(SATURDAY, SUNDAY), DayOfWeek.entries.toSet())
        for (days in patterns) {
            val id = repository.save(alarm(9, 0, days = days))
            assertEquals(days, repository.get(id)?.days)
        }
    }

    @Test
    fun toggleAndDelete() = runTest {
        val id = repository.save(alarm(6, 30))
        repository.setEnabled(id, false)
        assertEquals(false, repository.get(id)?.enabled)

        repository.delete(id)
        assertNull(repository.get(id))
    }

    @Test
    fun alarmsAreSortedByTime() = runTest {
        repository.save(alarm(9, 0))
        repository.save(alarm(5, 45))
        repository.save(alarm(6, 30))
        repository.alarms.test {
            assertEquals(listOf(5 to 45, 6 to 30, 9 to 0), awaitItem().map { it.hour to it.minute })
        }
    }
}

private class FakeAlarmDao : AlarmDao {
    private val rows = MutableStateFlow<Map<Long, AlarmEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<AlarmEntity>> =
        rows.map { it.values.sortedWith(compareBy(AlarmEntity::hour, AlarmEntity::minute)) }

    override suspend fun get(id: Long): AlarmEntity? = rows.value[id]

    // Mirrors Room's @Upsert: the new row id on insert, -1 on update.
    override suspend fun upsert(alarm: AlarmEntity): Long {
        if (alarm.id != 0L && alarm.id in rows.value) {
            rows.value += alarm.id to alarm
            return -1
        }
        val id = if (alarm.id != 0L) alarm.id else nextId++
        rows.value += id to alarm.copy(id = id)
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        rows.value[id]?.let { rows.value += id to it.copy(enabled = enabled) }
    }

    override suspend fun delete(id: Long) {
        rows.value -= id
    }
}
