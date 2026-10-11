package app.morrow.alarms

import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

private val london = TimeZone.of("Europe/London")
private val newYork = TimeZone.of("America/New_York")

private fun local(iso: String, zone: TimeZone = london) = LocalDateTime.parse(iso).toInstant(zone)

class FixedDatesTest {

    private fun alarm(days: Set<DayOfWeek>) =
        Alarm(3, 9, 0, "Stand-up", days, DefaultGames, enabled = true, timeZone = "America/New_York", zoneLabel = "New York")

    @Test
    fun aWeekdayMeetingGoesOverAsItsNextWeekOfDates() {
        val first = local("2026-03-06T09:00", newYork)
        val dates = alarm(DayOfWeek.entries.take(5).toSet()).fixedDates(first, newYork)
        // New York's clocks change on Sunday 8 March, so the times in London move from 14:00 to 13:00,
        // and the next Friday comes round an hour short of a week.
        assertEquals(
            listOf(
                "2026-03-06T14:00", "2026-03-09T13:00", "2026-03-10T13:00", "2026-03-11T13:00", "2026-03-12T13:00", "2026-03-13T13:00",
            ).map { local(it) },
            dates,
        )
    }

    @Test
    fun aWeeklyAlarmHasOneDateAWeek() {
        val first = local("2026-10-11T09:00", newYork)
        assertEquals(listOf(first), alarm(setOf(SUNDAY)).fixedDates(first, newYork))
    }

    @Test
    fun aDailyAlarmFillsTheWeek() {
        val first = local("2026-10-05T09:00", newYork)
        assertEquals(7, alarm(DayOfWeek.entries.toSet()).fixedDates(first, newYork).size)
    }

    @Test
    fun aOneOffAlarmHasOnlyItsOneDate() {
        val first = local("2026-10-05T09:00", newYork)
        assertEquals(listOf(first), alarm(emptySet()).fixedDates(first, newYork))
    }

    @Test
    fun eachDateHasItsOwnIdThatLeadsBackToTheAlarm() {
        val ids = (1..FIXED_DATE_SLOTS).map { fixedDateId(42, it) }
        assertEquals(FIXED_DATE_SLOTS, ids.toSet().size)
        assertEquals(setOf(42L), ids.map(::alarmIdOf).toSet())
        assertEquals(42L, alarmIdOf(42))
        assertFalse(42L in ids)
        // AlarmKit's ids hold 48 bits of Morrow's.
        assertEquals(0L, ids.max() shr 48)
        assertFailsWith<IllegalArgumentException> { fixedDateId(42, 0) }
    }
}
