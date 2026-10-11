package app.morrow.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
    zone: String? = null,
) = Alarm(id = id, hour = hour, minute = minute, label = "Test", days = days, games = DefaultGames, enabled = enabled, timeZone = zone)

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

    @Test
    fun aZonedAlarmKeepsItsCitysClockWhileTheClocksChangeInMarch() {
        // New York moves its clocks on 8 March, London on 29 March. For those three weeks 09:00 there is 13:00 here.
        val meeting = alarm(9, 0, days = DayOfWeek.entries.take(5).toSet(), zone = "America/New_York")
        val expected = mapOf(
            LocalDate(2026, 3, 6) to "14:00",
            LocalDate(2026, 3, 9) to "13:00",
            LocalDate(2026, 3, 27) to "13:00",
            LocalDate(2026, 3, 30) to "14:00",
        )
        expected.forEach { (date, londonTime) -> assertEquals(londonTime, ringsHere(meeting, date)) }
        assertKeepsNewYorkTime(meeting, LocalDate(2026, 3, 1), LocalDate(2026, 4, 3))
    }

    @Test
    fun aZonedAlarmKeepsItsCitysClockWhileTheClocksChangeInAutumn() {
        // London moves its clocks back on 25 October, New York on 1 November: one week at 13:00 here.
        val meeting = alarm(9, 0, days = DayOfWeek.entries.take(5).toSet(), zone = "America/New_York")
        val expected = mapOf(
            LocalDate(2026, 10, 23) to "14:00",
            LocalDate(2026, 10, 26) to "13:00",
            LocalDate(2026, 10, 30) to "13:00",
            LocalDate(2026, 11, 2) to "14:00",
        )
        expected.forEach { (date, londonTime) -> assertEquals(londonTime, ringsHere(meeting, date)) }
        assertKeepsNewYorkTime(meeting, LocalDate(2026, 10, 18), LocalDate(2026, 11, 6))
    }

    @Test
    fun repeatDaysCountInTheAlarmsZone() {
        // 22:00 on Sunday in Los Angeles is 06:00 on Monday in London.
        val sundayNight = alarm(22, 0, days = setOf(SUNDAY), zone = "America/Los_Angeles")
        assertEquals(local("2026-10-12T06:00"), sundayNight.nextRing(local("2026-10-11T12:00"), london))
        assertEquals(local("2026-10-19T06:00"), sundayNight.nextRing(local("2026-10-12T06:00"), london))
        // Monday at 05:00 in London is still Sunday night there.
        assertEquals(local("2026-10-12T06:00"), sundayNight.nextRing(local("2026-10-12T05:00"), london))
    }

    @Test
    fun anUnknownZoneFollowsThePhone() {
        val lost = alarm(7, 30, zone = "Atlantis/Lost_City")
        assertEquals(london, lost.clockZone(london))
        assertEquals(local("2026-10-05T07:30"), lost.nextRing(local("2026-10-05T06:00"), london))
    }

    @Test
    fun nextAlarmComparesZonedAlarmsByWhenTheyReallyRing() {
        // 08:00 in Tokyo is midnight in London, long before a 07:00 London alarm.
        val tokyo = alarm(8, 0, id = 1, zone = "Asia/Tokyo")
        val home = alarm(7, 0, id = 2)
        val next = listOf(home, tokyo).nextAlarm(local("2026-10-05T23:00"), london)
        assertEquals(1L, next?.alarm?.id)
        assertEquals(local("2026-10-06T00:00"), next?.at)
    }

    @Test
    fun theOccurrenceNearANowIsTheOneRinging() {
        val meeting = alarm(9, 0, days = setOf(MONDAY), zone = "America/New_York")
        // Rang at 14:00 in London on a Monday; a moment later, that's still the occurrence.
        assertEquals(local("2026-10-05T14:00"), meeting.occurrenceNear(local("2026-10-05T14:00:20"), london))
        assertEquals(local("2026-10-05T14:00"), meeting.occurrenceNear(local("2026-10-05T13:59:58"), london))
    }

    /** The London wall-clock time of [alarm]'s first ring on or after London's midnight starting [date]. */
    private fun ringsHere(alarm: Alarm, date: LocalDate): String {
        val at = alarm.nextRing(LocalDateTime(date, LocalTime(0, 0)).toInstant(london), london)
        val here = at.toLocalDateTime(london)
        assertEquals(date, here.date)
        return here.time.toString()
    }

    /** Walks ring by ring from [from] to [until]: every ring is on a New York weekday at 09:00, and none is missed. */
    private fun assertKeepsNewYorkTime(alarm: Alarm, from: LocalDate, until: LocalDate) {
        var at = LocalDateTime(from, LocalTime(0, 0)).toInstant(newYork)
        var expectedDate = from
        while (true) {
            at = alarm.nextRing(at, london)
            val there = at.toLocalDateTime(newYork)
            if (there.date > until) break
            while (expectedDate.dayOfWeek.isoDayNumber > 5) expectedDate += DatePeriod(days = 1)
            assertEquals(LocalDateTime(expectedDate, LocalTime(9, 0)), there)
            expectedDate += DatePeriod(days = 1)
        }
    }
}
