package app.morrow.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val london = TimeZone.of("Europe/London")
private val newYork = TimeZone.of("America/New_York")

private fun local(iso: String, zone: TimeZone = london): Instant = LocalDateTime.parse(iso).toInstant(zone)

private fun alarm(
    hour: Int,
    minute: Int,
    days: Set<DayOfWeek> = emptySet(),
    enabled: Boolean = true,
    id: Long = 1,
) = Alarm(id = id, hour = hour, minute = minute, label = "Test", days = days, mission = MissionType.SUMS, enabled = enabled)

// 2026-10-05 is a Monday.
class NextOccurrenceTest {

    @Test
    fun oneOffLaterToday() {
        val next = alarm(7, 30).nextOccurrence(after = local("2026-10-05T06:00"), timeZone = london)
        assertEquals(local("2026-10-05T07:30"), next)
    }

    @Test
    fun oneOffAlreadyPassedMovesToTomorrow() {
        val next = alarm(7, 30).nextOccurrence(after = local("2026-10-05T08:00"), timeZone = london)
        assertEquals(local("2026-10-06T07:30"), next)
    }

    @Test
    fun alarmTimeEqualToNowIsNotNext() {
        val next = alarm(7, 30).nextOccurrence(after = local("2026-10-05T07:30"), timeZone = london)
        assertEquals(local("2026-10-06T07:30"), next)
    }

    @Test
    fun midnightRollover() {
        val now = local("2026-10-05T23:50")
        val next = alarm(0, 10).nextOccurrence(after = now, timeZone = london)
        assertEquals(local("2026-10-06T00:10"), next)
        assertEquals(20.minutes, next - now)
    }

    @Test
    fun repeatingSkipsDaysThatAreOff() {
        val next = alarm(7, 30, days = setOf(SATURDAY, SUNDAY)).nextOccurrence(local("2026-10-05T06:00"), london)
        assertEquals(local("2026-10-10T07:30"), next)
    }

    @Test
    fun repeatingRingsLaterTheSameDay() {
        val next = alarm(7, 30, days = setOf(MONDAY)).nextOccurrence(local("2026-10-05T06:00"), london)
        assertEquals(local("2026-10-05T07:30"), next)
    }

    @Test
    fun repeatingThatAlreadyRangTodayWaitsAWeek() {
        val next = alarm(7, 30, days = setOf(MONDAY)).nextOccurrence(local("2026-10-05T08:00"), london)
        assertEquals(local("2026-10-12T07:30"), next)
    }

    @Test
    fun everyDayRollsOverMidnight() {
        val next = alarm(0, 10, days = DayOfWeek.entries.toSet()).nextOccurrence(local("2026-10-05T23:50"), london)
        assertEquals(local("2026-10-06T00:10"), next)
    }

    @Test
    fun noEnabledAlarmsMeansNoNextAlarm() {
        val alarms = listOf(alarm(6, 30, enabled = false, id = 1), alarm(9, 0, enabled = false, id = 2))
        assertNull(alarms.nextAlarm(local("2026-10-05T06:00"), london))
        assertNull(emptyList<Alarm>().nextAlarm(local("2026-10-05T06:00"), london))
    }

    @Test
    fun nextAlarmPicksTheEarliestEnabledOne() {
        val early = alarm(6, 30, id = 1, enabled = false)
        val gym = alarm(5, 45, days = setOf(SATURDAY), id = 2)
        val work = alarm(7, 0, id = 3)
        val next = listOf(early, gym, work).nextAlarm(local("2026-10-05T06:00"), london)
        assertEquals(work, next?.alarm)
        assertEquals(local("2026-10-05T07:00"), next?.at)
    }

    @Test
    fun timeInTheSpringForwardGapRingsAnHourLater() {
        // 2026-03-29 01:00 GMT jumps to 02:00 BST in London, so 01:30 never happens.
        val next = alarm(1, 30).nextOccurrence(local("2026-03-28T23:00"), london)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next)
    }

    @Test
    fun timeInTheFallBackOverlapRingsOnlyOnce() {
        // 2026-10-25 02:00 BST falls back to 01:00 GMT, so 01:30 happens twice.
        val first = alarm(1, 30).nextOccurrence(local("2026-10-25T00:00"), london)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), first)

        val afterRinging = alarm(1, 30).nextOccurrence(first, london)
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), afterRinging)
    }

    @Test
    fun nightIsAnHourShorterWhenTheClocksGoForward() {
        val now = local("2026-03-28T23:00")
        val next = alarm(7, 0).nextOccurrence(now, london)
        assertEquals(7.hours, next - now)
    }

    @Test
    fun alarmFollowsTheLocalClockAfterATimezoneChange() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        assertEquals(local("2026-10-06T07:00", london), alarm(7, 0).nextOccurrence(now, london))
        assertEquals(local("2026-10-06T07:00", newYork), alarm(7, 0).nextOccurrence(now, newYork))
    }

    @Test
    fun repeatDaysUseTheLocalDate() {
        // 23:30 UTC on Monday is already Tuesday in London but still Monday evening in New York.
        val now = Instant.parse("2026-10-05T23:30:00Z")
        val mondayNight = alarm(21, 0, days = setOf(MONDAY))
        assertEquals(local("2026-10-12T21:00", london), mondayNight.nextOccurrence(now, london))
        assertEquals(local("2026-10-05T21:00", newYork), mondayNight.nextOccurrence(now, newYork))
    }
}
