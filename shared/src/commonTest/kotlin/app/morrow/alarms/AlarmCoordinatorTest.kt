package app.morrow.alarms

import app.morrow.data.AlarmRepository
import app.morrow.data.FakeAlarmDao
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.platform.AlarmScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

private val london = TimeZone.of("Europe/London")

private fun local(iso: String): Instant = LocalDateTime.parse(iso).toInstant(london)

class AlarmCoordinatorTest {

    private val scheduler = FakeAlarmScheduler()
    private val repository = AlarmRepository(FakeAlarmDao())
    private val clock = FakeClock(local("2026-10-05T06:00"))
    private val coordinator = AlarmCoordinator(repository, scheduler, clock) { london }

    private fun alarm(hour: Int, minute: Int, days: Set<DayOfWeek> = emptySet(), enabled: Boolean = true, id: Long = 0) =
        Alarm(id = id, hour = hour, minute = minute, label = "Work", days = days, mission = MissionType.SUMS, enabled = enabled)

    @Test
    fun savingAnEnabledAlarmSchedulesItsNextOccurrence() = runTest {
        val id = coordinator.save(alarm(7, 30))
        assertEquals(local("2026-10-05T07:30"), scheduler.scheduled[id])
    }

    @Test
    fun savingADisabledAlarmCancelsIt() = runTest {
        val id = coordinator.save(alarm(7, 30))
        coordinator.save(alarm(7, 30, enabled = false, id = id))
        assertNull(scheduler.scheduled[id])
    }

    @Test
    fun movingAnAlarmReschedulesIt() = runTest {
        val id = coordinator.save(alarm(7, 30))
        coordinator.save(alarm(8, 0, id = id))
        assertEquals(local("2026-10-05T08:00"), scheduler.scheduled[id])
    }

    @Test
    fun togglingSchedulesAndCancels() = runTest {
        val id = coordinator.save(alarm(7, 30))

        coordinator.setEnabled(id, false)
        assertNull(scheduler.scheduled[id])
        assertEquals(false, repository.get(id)?.enabled)

        coordinator.setEnabled(id, true)
        assertEquals(local("2026-10-05T07:30"), scheduler.scheduled[id])
    }

    @Test
    fun deletingCancels() = runTest {
        val id = coordinator.save(alarm(7, 30))
        coordinator.delete(id)
        assertNull(scheduler.scheduled[id])
        assertNull(repository.get(id))
    }

    @Test
    fun rescheduleAllRestoresEveryEnabledAlarm() = runTest {
        val on = repository.save(alarm(7, 30))
        val off = repository.save(alarm(9, 0, enabled = false))
        scheduler.scheduled[off] = local("2026-10-05T09:00")

        coordinator.rescheduleAll()

        assertEquals(mapOf(on to local("2026-10-05T07:30")), scheduler.scheduled)
    }

    @Test
    fun rescheduleAllUsesTheCurrentTimezone() = runTest {
        var zone = london
        val travelling = AlarmCoordinator(repository, scheduler, clock) { zone }
        val id = travelling.save(alarm(7, 30))

        zone = TimeZone.of("America/New_York")
        travelling.rescheduleAll()

        assertEquals(LocalDateTime.parse("2026-10-05T07:30").toInstant(zone), scheduler.scheduled[id])
    }

    @Test
    fun aOneOffAlarmTurnsItselfOffAfterRinging() = runTest {
        val id = coordinator.save(alarm(7, 30))
        clock.now = local("2026-10-05T07:30")

        coordinator.onRang(id)

        assertEquals(false, repository.get(id)?.enabled)
        assertNull(scheduler.scheduled[id])
    }

    @Test
    fun aRepeatingAlarmSchedulesItsNextDayAfterRinging() = runTest {
        val id = coordinator.save(alarm(7, 30, days = setOf(MONDAY)))
        clock.now = local("2026-10-05T07:30")

        coordinator.onRang(id)

        assertEquals(true, repository.get(id)?.enabled)
        assertEquals(local("2026-10-12T07:30"), scheduler.scheduled[id])
    }

    @Test
    fun ringingSlightlyEarlyDoesNotScheduleTheSameMinuteAgain() = runTest {
        val id = coordinator.save(alarm(7, 30, days = DayOfWeek.entries.toSet()))
        clock.now = local("2026-10-05T07:29:59.900")

        coordinator.onRang(id)

        assertEquals(local("2026-10-06T07:30"), scheduler.scheduled[id])
    }

    @Test
    fun ringingForADeletedAlarmDoesNothing() = runTest {
        coordinator.onRang(42)
        assertEquals(emptyMap(), scheduler.scheduled)
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    val scheduled = mutableMapOf<Long, Instant>()

    override fun schedule(alarm: Alarm, at: Instant) {
        scheduled[alarm.id] = at
    }

    override fun cancel(alarmId: Long) {
        scheduled.remove(alarmId)
    }
}

class FakeClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
