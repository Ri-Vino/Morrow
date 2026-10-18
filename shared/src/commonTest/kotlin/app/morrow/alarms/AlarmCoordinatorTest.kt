package app.morrow.alarms

import app.morrow.data.AlarmRepository
import app.morrow.data.FakeAlarmDao
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.platform.AlarmScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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

    private fun alarm(
        hour: Int,
        minute: Int,
        days: Set<DayOfWeek> = emptySet(),
        enabled: Boolean = true,
        id: Long = 0,
        zone: String? = null,
    ) = Alarm(id = id, hour = hour, minute = minute, label = "Work", days = days, games = DefaultGames, enabled = enabled, timeZone = zone)

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
    fun catchingUpTurnsOffOneOffAlarmsThatRang() = runTest {
        val rang = coordinator.save(alarm(7, 30))
        val waiting = coordinator.save(alarm(9, 0))

        coordinator.catchUp(stillScheduled = setOf(waiting))

        assertEquals(false, repository.get(rang)?.enabled)
        assertEquals(true, repository.get(waiting)?.enabled)
    }

    @Test
    fun catchingUpPutsBackLostRepeatingAlarms() = runTest {
        val id = repository.save(alarm(7, 30, days = setOf(MONDAY)))

        coordinator.catchUp(stillScheduled = emptySet())

        assertEquals(true, repository.get(id)?.enabled)
        assertEquals(local("2026-10-05T07:30"), scheduler.scheduled[id])
    }

    @Test
    fun catchingUpLeavesDisabledAlarmsAlone() = runTest {
        val id = repository.save(alarm(7, 30, enabled = false))

        coordinator.catchUp(stillScheduled = emptySet())

        assertEquals(false, repository.get(id)?.enabled)
        assertNull(scheduler.scheduled[id])
    }

    @Test
    fun aZonedAlarmIsScheduledByItsCitysClock() = runTest {
        val id = coordinator.save(alarm(9, 0, zone = "America/New_York"))
        assertEquals(local("2026-10-05T14:00"), scheduler.scheduled[id])
    }

    @Test
    fun aZonedAlarmRingsThroughTheWeeksTheClocksDisagree() = runTest {
        // New York changes its clocks on 8 March, London on 29 March; the meeting stays at 09:00 New York time.
        clock.now = local("2026-03-02T00:00")
        val id = coordinator.save(alarm(9, 0, days = DayOfWeek.entries.take(5).toSet(), zone = "America/New_York"))
        val rings = mutableListOf<LocalDateTime>()
        while (true) {
            val at = scheduler.scheduled.getValue(id)
            if (at > local("2026-04-03T23:59")) break
            rings += at.toLocalDateTime(london)
            clock.now = at
            coordinator.onRang(id)
        }

        assertEquals(25, rings.size)
        rings.forEach { ring ->
            val day = ring.date.day
            val expected = if (day in 9..27) 13 else 14
            assertEquals(LocalTime(expected, 0), ring.time, "on ${ring.date}")
        }
    }

    @Test
    fun changingThePhonesZoneKeepsZonedAlarmsAndMovesTheOthers() = runTest {
        var zone = london
        val travelling = AlarmCoordinator(repository, scheduler, clock) { zone }
        val meeting = travelling.save(alarm(9, 0, zone = "America/New_York"))
        val wakeUp = travelling.save(alarm(7, 30))
        val meetingAt = scheduler.scheduled.getValue(meeting)

        zone = TimeZone.of("Asia/Tokyo")
        travelling.rescheduleAll()

        assertEquals(meetingAt, scheduler.scheduled[meeting])
        assertEquals(LocalDateTime.parse("2026-10-06T07:30").toInstant(zone), scheduler.scheduled[wakeUp])
    }

    @Test
    fun aRepeatingZonedAlarmCountsItsDaysInItsZone() = runTest {
        // Sunday 22:00 in Los Angeles is Monday 06:00 in London.
        val id = coordinator.save(alarm(22, 0, days = setOf(DayOfWeek.SUNDAY), zone = "America/Los_Angeles"))
        assertEquals(local("2026-10-12T06:00"), scheduler.scheduled[id])

        clock.now = local("2026-10-12T06:00")
        coordinator.onRang(id)

        assertEquals(local("2026-10-19T06:00"), scheduler.scheduled[id])
    }

    @Test
    fun anUnknownZoneRingsByThePhonesClock() = runTest {
        val id = coordinator.save(alarm(7, 30, zone = "Atlantis/Lost_City"))
        assertEquals(local("2026-10-05T07:30"), scheduler.scheduled[id])
    }

    @Test
    fun catchingUpLeavesAZonedAlarmThatStillHasDates() = runTest {
        val zoned = coordinator.save(alarm(9, 0, days = setOf(MONDAY), zone = "America/New_York"))
        val plain = coordinator.save(alarm(7, 30, days = setOf(MONDAY)))
        scheduler.scheduled.clear()

        coordinator.catchUp(stillScheduled = setOf(zoned, plain))

        assertEquals(emptyMap(), scheduler.scheduled)
    }

    @Test
    fun catchingUpTopsUpAZonedAlarmWhoseDatesRanOut() = runTest {
        val zoned = coordinator.save(alarm(9, 0, days = setOf(MONDAY), zone = "America/New_York"))
        scheduler.scheduled.clear()

        coordinator.catchUp(stillScheduled = emptySet())

        assertEquals(mapOf(zoned to local("2026-10-05T14:00")), scheduler.scheduled)
    }

    @Test
    fun ringingForADeletedAlarmDoesNothing() = runTest {
        coordinator.onRang(42)
        assertEquals(emptyMap(), scheduler.scheduled)
    }

    @Test
    fun aTemporaryAlarmDeletesItselfOnceDoneWithAndCanBeOfferedBack() = runTest {
        val id = coordinator.save(alarm(7, 30).copy(temporary = true))
        coordinator.onRang(id)
        coordinator.onFinished(id)

        assertNull(repository.get(id))
        assertNull(scheduler.scheduled[id])
        assertEquals(id, coordinator.deletedWhenDone.value?.id)
        coordinator.forgetDeletedWhenDone()
        assertNull(coordinator.deletedWhenDone.value)
    }

    @Test
    fun anOrdinaryAlarmStaysOnceDoneWith() = runTest {
        val id = coordinator.save(alarm(7, 30))
        coordinator.onRang(id)
        coordinator.onFinished(id)

        assertEquals(false, repository.get(id)?.enabled)
        assertNull(coordinator.deletedWhenDone.value)
    }

    @Test
    fun catchingUpDeletesATemporaryAlarmTheSystemHasRung() = runTest {
        val temporary = coordinator.save(alarm(7, 30).copy(temporary = true))
        val once = coordinator.save(alarm(7, 45))
        coordinator.catchUp(stillScheduled = emptySet())

        assertNull(repository.get(temporary))
        assertEquals(false, repository.get(once)?.enabled)
        assertEquals(temporary, coordinator.deletedWhenDone.value?.id)
        // Still on when the system rang it, but offered back off, so Undo can't set it for tomorrow.
        assertEquals(false, coordinator.deletedWhenDone.value?.enabled)
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
