package app.morrow.ui.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeEntryTest {

    private val sevenThirty = TimeEntry.at(7 * 60 + 30)

    private fun TimeEntry.keys(vararg digits: Int) = digits.fold(this) { entry, digit -> entry.type(digit) }

    private fun TimeEntry.shown() = "${hours.padStart(2, '0')}:${minutes.padStart(2, '0')}"

    @Test
    fun opensOnTheAlarmsTimeWithTheHoursPicked() {
        assertEquals(TimeEntry("07", "30", TimePart.HOURS, fresh = true), sevenThirty)
        assertEquals(7 * 60 + 30, sevenThirty.minuteOfDay)
    }

    @Test
    fun fourDigitsTypeAWholeTime() {
        val entry = sevenThirty.keys(0, 6, 4, 5)
        assertEquals("06:45", entry.shown())
        assertEquals(6 * 60 + 45, entry.minuteOfDay)
    }

    @Test
    fun twoHourDigitsMoveOnToTheMinutesWhichKeepTheirTimeUntilTyped() {
        val entry = sevenThirty.keys(2, 1)
        assertEquals(TimePart.MINUTES, entry.part)
        assertEquals("21:30", entry.shown())
        assertEquals(21 * 60 + 30, entry.minuteOfDay)
    }

    @Test
    fun anHourOfThreeOrMoreCanOnlyBeOneDigit() {
        val entry = sevenThirty.keys(9)
        assertEquals("09:30", entry.shown())
        assertEquals(TimePart.MINUTES, entry.part)
    }

    @Test
    fun afterATwoOnlyHoursUpTo23CanBeTyped() {
        val two = sevenThirty.keys(2)
        assertTrue(two.canType(3))
        assertFalse(two.canType(4))
        assertEquals(two, two.type(7))
        assertEquals("23:30", two.keys(3).shown())
    }

    @Test
    fun aFirstMinuteDigitOfSixOrMoreIsAWholeMinute() {
        assertEquals("08:07", sevenThirty.keys(8, 7).shown())
        assertEquals("08:40", sevenThirty.keys(8, 4, 0).shown())
        // A third minute digit starts the minutes again.
        assertEquals("08:15", sevenThirty.keys(8, 4, 0, 1, 5).shown())
    }

    @Test
    fun oneHourDigitIsAnHourOnItsOwn() {
        val entry = sevenThirty.keys(1)
        assertEquals(TimePart.HOURS, entry.part)
        assertEquals(60 + 30, entry.minuteOfDay)
    }

    @Test
    fun pickingTheMinutesTypesJustThose() {
        val entry = sevenThirty.pick(TimePart.MINUTES).keys(4, 5)
        assertEquals(7 * 60 + 45, entry.minuteOfDay)
    }

    @Test
    fun deleteEmptiesAPickedPartThenGoesBackToTheHours() {
        val minutes = sevenThirty.keys(1, 9).delete()
        assertEquals(TimeEntry("19", "", TimePart.MINUTES, fresh = false), minutes)
        assertEquals(19 * 60, minutes.minuteOfDay)

        val hours = minutes.delete()
        assertEquals(TimeEntry("1", "", TimePart.HOURS, fresh = false), hours)
        val none = hours.delete()
        assertNull(none.minuteOfDay)
        assertEquals(none, none.delete())
    }
}
