package app.morrow.ui.home

import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.platform.AlarmPermission
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val london = TimeZone.of("Europe/London")
private val weekdays = setOf(MONDAY, DayOfWeek.TUESDAY, WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY)

// 2026-10-06 is a Tuesday.
class HomeModelTest {

    private fun alarm(
        hour: Int,
        minute: Int,
        label: String = "Work days",
        days: Set<DayOfWeek> = emptySet(),
        mission: MissionType = MissionType.SUMS,
        enabled: Boolean = true,
        id: Long = 1,
    ) = Alarm(id, hour, minute, label, days, mission, enabled)

    private fun home(at: String, vararg alarms: Alarm, missing: List<AlarmPermission> = emptyList()) =
        homeModel(alarms.toList(), LocalDateTime.parse(at).toInstant(london), london, missing)

    @Test
    fun greetingFollowsTheTimeOfDay() {
        assertEquals("Still up", home("2026-10-06T01:20").greeting)
        assertEquals("Good morning", home("2026-10-06T07:40").greeting)
        assertEquals("Good afternoon", home("2026-10-06T12:30").greeting)
        assertEquals("Good evening", home("2026-10-06T18:00").greeting)
        assertEquals("Good night", home("2026-10-06T21:40").greeting)
    }

    @Test
    fun dateLabelSpellsOutTheDay() {
        assertEquals("Tuesday 6 October", home("2026-10-06T07:40").dateLabel)
    }

    @Test
    fun noAlarmsMeansNoSleepCardAndAnEmptyList() {
        val model = home("2026-10-06T21:40")
        assertNull(model.sleepCard)
        assertTrue(model.rows.isEmpty())
        assertEquals("", model.alarmCount)
    }

    @Test
    fun allAlarmsOffLetsPipSleepIn() {
        val card = home("2026-10-06T21:40", alarm(6, 30, enabled = false)).sleepCard!!
        assertEquals(SleepCard("No alarms are on", "Sleep in", "Pip will let you rest. Switch an alarm on when you need one.", night = false), card)
    }

    @Test
    fun atBedtimeTheCardCountsSleep() {
        val card = home("2026-10-06T23:10", alarm(6, 30, days = weekdays)).sleepCard!!
        assertEquals("Sleep now and you’ll get", card.lead)
        assertEquals("7h 20m", card.big)
        assertEquals("That’s about 4 full sleep cycles. Pip wakes you tomorrow at 06:30.", card.detail)
        assertTrue(card.night)
    }

    @Test
    fun oneSleepCycleIsSingular() {
        val card = home("2026-10-06T23:00", alarm(0, 45)).sleepCard!!
        assertEquals("That’s about 1 full sleep cycle. Pip wakes you tomorrow at 00:45.", card.detail)
    }

    @Test
    fun duringTheDayTheCardCountsDown() {
        val card = home("2026-10-06T13:00", alarm(15, 30, label = "Gym", mission = MissionType.SCAN)).sleepCard!!
        assertEquals("Pip wakes you in", card.lead)
        assertEquals("2h 30m", card.big)
        assertEquals("Gym, today at 15:30 · Scan mission", card.detail)
    }

    @Test
    fun underAnHourShowsMinutes() {
        assertEquals("45 min", home("2026-10-06T13:00", alarm(13, 45)).sleepCard!!.big)
        assertEquals("1h 05m", home("2026-10-06T13:00", alarm(14, 5)).sleepCard!!.big)
    }

    @Test
    fun partMinutesRoundUpSoTheCountMatchesTheClock() {
        // The minute ticker fires a few milliseconds after the minute starts.
        assertEquals("7h 20m", home("2026-10-06T23:10:00.005", alarm(6, 30)).sleepCard!!.big)
        assertEquals("1 min", home("2026-10-06T04:51:50", alarm(4, 52)).sleepCard!!.big)
    }

    @Test
    fun aWakeUpMoreThanADayAwayShowsTheDay() {
        val card = home("2026-10-06T08:00", alarm(6, 30, label = "Work", days = setOf(MONDAY))).sleepCard!!
        assertEquals("Next wake-up", card.lead)
        assertEquals("Mon 06:30", card.big)
        assertEquals("Work · Sums mission. Pip will be up before you.", card.detail)
    }

    @Test
    fun lostExactAlarmsShowsTheBannerAndPausesTheCard() {
        val model = home("2026-10-06T23:10", alarm(6, 30), missing = listOf(AlarmPermission.EXACT_ALARMS))
        assertEquals("Morrow lost permission to set exact alarms. Nothing will go off until you allow it again.", model.banner)
        assertEquals("Paused until alarms are allowed", model.sleepCard!!.lead)
    }

    @Test
    fun bannerNamesEveryMissingRequiredPermission() {
        val model = home(
            "2026-10-06T23:10",
            missing = listOf(AlarmPermission.NOTIFICATIONS, AlarmPermission.FULL_SCREEN, AlarmPermission.BATTERY),
        )
        assertEquals("Morrow needs notifications and full-screen alarm before any alarm can go off.", model.banner)
    }

    @Test
    fun batteryAloneIsNotABlocker() {
        assertNull(home("2026-10-06T23:10", alarm(6, 30), missing = listOf(AlarmPermission.BATTERY)).banner)
    }

    @Test
    fun rowsDescribeEachAlarm() {
        val model = home(
            "2026-10-06T21:40",
            alarm(6, 30, "Work days", weekdays, id = 1),
            alarm(9, 0, "Slow Sunday", setOf(SUNDAY), MissionType.BREATHE, id = 2),
            alarm(5, 45, "Gym", setOf(MONDAY, WEDNESDAY, FRIDAY), MissionType.SCAN, enabled = false, id = 3),
        )
        assertEquals("2 of 3 on", model.alarmCount)
        assertEquals(
            listOf(
                AlarmRow(1, "06:30", "Work days · Weekdays", MissionType.SUMS, enabled = true, sunUp = true),
                AlarmRow(2, "09:00", "Slow Sunday · Sun", MissionType.BREATHE, enabled = true, sunUp = true),
                AlarmRow(3, "05:45", "Gym · Mon Wed Fri", MissionType.SCAN, enabled = false, sunUp = false),
            ),
            model.rows,
        )
    }

    @Test
    fun repeatPatternsHaveFriendlyNames() {
        assertEquals("Every day", describeDays(DayOfWeek.entries.toSet()))
        assertEquals("Weekdays", describeDays(weekdays))
        assertEquals("Weekends", describeDays(setOf(SATURDAY, SUNDAY)))
        assertEquals("Once", describeDays(emptySet()))
        assertEquals("Mon Sat", describeDays(setOf(SATURDAY, MONDAY)))
    }
}
