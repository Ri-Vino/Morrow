package app.morrow.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuickAlarmsTest {
    private val london = TimeZone.of("Europe/London")
    private fun at(text: String) = LocalDateTime.parse(text).toInstant(london)

    @Test
    fun aNapRingsItsLengthFromNowToTheNearestMinute() {
        val nap = quickAlarm(20, at("2026-10-18T14:02:20"), london, NAP_NAME)
        assertEquals(14 to 22, nap.hour to nap.minute)
        assertEquals("Nap", nap.label)
        assertTrue(nap.temporary)
        assertTrue(nap.days.isEmpty())
        assertEquals(14 to 23, quickAlarm(20, at("2026-10-18T14:02:40"), london, NAP_NAME).let { it.hour to it.minute })
    }

    @Test
    fun aQuickAlarmCanRunPastMidnight() {
        val late = quickAlarm(60, at("2026-10-18T23:30:00"), london, QUICK_ALARM_NAME)
        assertEquals(0 to 30, late.hour to late.minute)
    }

    @Test
    fun aQuickAlarmIsAtLeastAMinuteAway() {
        assertFailsWith<IllegalArgumentException> { quickAlarm(0, at("2026-10-18T14:00:00"), london, NAP_NAME) }
    }

    @Test
    fun aNewTemporaryAlarmStartsHalfAnHourOutOnAFiveMinuteMark() {
        val alarm = newTemporaryAlarm(at("2026-10-18T14:02:00"), london)
        assertEquals(14 to 35, alarm.hour to alarm.minute)
        assertTrue(alarm.temporary)
        assertEquals(0 to 0, newTemporaryAlarm(at("2026-10-18T23:28:00"), london).let { it.hour to it.minute })
    }

    @Test
    fun aTemporaryAlarmCantRepeat() {
        assertFailsWith<IllegalArgumentException> {
            quickAlarm(5, at("2026-10-18T14:00:00"), london, NAP_NAME).copy(days = setOf(kotlinx.datetime.DayOfWeek.MONDAY))
        }
    }

    @Test
    fun typingMinutesStaysInRange() {
        assertEquals("", typeMinutes("", 0))
        assertEquals("4", typeMinutes("", 4))
        assertEquals("45", typeMinutes("4", 5))
        assertEquals("450", typeMinutes("45", 0))
        assertEquals("9", typeMinutes("72", 9))
        assertEquals("", typeMinutes("720", 0))
        assertEquals("6", typeMinutes("6", 6, max = 60))
    }
}
