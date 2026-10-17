package app.morrow.data

import app.cash.turbine.test
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
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
        Alarm(id = id, hour = hour, minute = minute, label = "Gym", days = days, games = listOf(GameStep(MissionType.SCAN, 1)), enabled = true)

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
    fun aFreshInstallStartsWithAnOffAlarmAtEightOnWeekdays() = runTest {
        repository.seedFirstAlarm()
        val first = repository.getAll().single()
        assertEquals(8 to 0, first.hour to first.minute)
        assertEquals(setOf(MONDAY, DayOfWeek.TUESDAY, WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY), first.days)
        assertEquals(false, first.enabled)

        // Only ever the once: not again, and not over alarms already there.
        repository.seedFirstAlarm()
        assertEquals(1, repository.getAll().size)
        repository.delete(first.id)
        repository.save(alarm(6, 30))
        repository.seedFirstAlarm()
        assertEquals(listOf(6 to 30), repository.getAll().map { it.hour to it.minute })
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
