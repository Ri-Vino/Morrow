package app.morrow.ui.timers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimerEntryTest {

    private fun typed(vararg keys: String) = keys.fold(TimerEntry()) { entry, key -> entry.type(key) }

    private fun TimerEntry.readout() = "$hoursText:$minutesText:$secondsText"

    @Test
    fun anEmptyEntryShowsZerosAndCantStart() {
        val entry = TimerEntry()

        assertEquals("00:00:00", entry.readout())
        assertEquals(Duration.ZERO, entry.duration)
        assertFalse(entry.canStart)
        assertEquals("No time set yet", entry.label)
    }

    @Test
    fun digitsShiftInFromTheRight() {
        assertEquals("00:00:01", typed("1").readout())
        assertEquals("00:00:15", typed("1", "5").readout())
        assertEquals("00:01:50", typed("1", "5", "0").readout())
        assertEquals("00:15:00", typed("1", "5", "0", "0").readout())
        assertEquals("01:23:45", typed("1", "2", "3", "4", "5").readout())
        assertEquals("12:34:56", typed("1", "2", "3", "4", "5", "6").readout())
    }

    @Test
    fun untypedDigitsAreMutedUntilTheTypingReachesThem() {
        fun reached(entry: TimerEntry) = listOf(entry.hoursTyped, entry.minutesTyped, entry.secondsTyped)

        assertEquals(listOf(false, false, false), reached(TimerEntry()))
        assertEquals(listOf(false, false, true), reached(typed("5")))
        assertEquals(listOf(false, false, true), reached(typed("5", "0")))
        assertEquals(listOf(false, true, true), reached(typed("5", "0", "0")))
        assertEquals(listOf(false, true, true), reached(typed("1", "2", "3", "4")))
        assertEquals(listOf(true, true, true), reached(typed("1", "2", "3", "4", "5")))
    }

    @Test
    fun leadingZerosAddNothing() {
        assertEquals("", typed("0").digits)
        assertEquals("", typed("00").digits)
        assertEquals("", typed("0", "0", "0").digits)
        assertEquals("100", typed("1", "00").digits)
        assertEquals("1000", typed("1", "0", "0", "0").digits)
        assertEquals("500", typed("0", "5", "00").digits)
    }

    @Test
    fun aSeventhDigitIsIgnored() {
        val full = typed("1", "2", "3", "4", "5", "6")

        assertEquals("123456", full.type("7").digits)
        assertEquals("123456", full.type("0").digits)
        assertEquals("123456", full.type("00").digits)
    }

    @Test
    fun doubleZeroIsTakenWholeOrNotAtAll() {
        val five = typed("1", "2", "3", "4", "5")

        assertEquals("12345", five.type("00").digits)
        assertEquals("123450", five.type("0").digits)
        assertEquals("123400", typed("1", "2", "3", "4").type("00").digits)
    }

    @Test
    fun deleteTakesOffTheLastDigit() {
        assertEquals("100", typed("1", "0", "0", "0").deleteLast().digits)
        assertEquals("1", typed("1", "5").deleteLast().digits)
        assertEquals("", typed("1").deleteLast().digits)
        assertEquals("", TimerEntry().deleteLast().digits)
        assertEquals("00:00:10", typed("1", "0", "0").deleteLast().readout())
    }

    @Test
    fun theDigitsSetTheLength() {
        assertEquals(10.minutes, typed("1", "0", "0", "0").duration)
        assertEquals(1.minutes + 30.seconds, typed("1", "3", "0").duration)
        assertEquals(45.seconds, typed("4", "5").duration)
        assertEquals(1.hours, typed("1", "0", "0", "0", "0").duration)
        assertEquals(12.hours + 34.minutes + 56.seconds, typed("1", "2", "3", "4", "5", "6").duration)
    }

    @Test
    fun minutesAndSecondsOver59AddUp() {
        assertEquals(99.seconds, typed("9", "9").duration)
        assertEquals(99.minutes + 99.seconds, typed("9", "9", "9", "9").duration)
        assertEquals(99.hours + 99.minutes + 99.seconds, typed("9", "9", "9", "9", "9", "9").duration)
        // The digits stay as typed, so what's on screen is what was pressed.
        assertEquals("00:99:99", typed("9", "9", "9", "9").readout())
    }

    @Test
    fun screenReadersHearTheLengthRatherThanTheDigits() {
        assertEquals("Timer for 10 min", typed("1", "0", "0", "0").label)
        assertEquals("Timer for 45 s", typed("4", "5").label)
        assertEquals("Timer for 1 h", typed("1", "0", "0", "0", "0").label)
        assertEquals("Timer for 1 h 41 min", typed("9", "9", "9", "9").label)
        assertEquals("No time set yet", typed("0", "0").label)
    }

    @Test
    fun startNeedsSomeTime() {
        assertFalse(TimerEntry().canStart)
        assertFalse(typed("0", "0").canStart)
        assertTrue(typed("1").canStart)
    }

    @Test
    fun savedDigitsComeBackCleaned() {
        assertEquals("1500", TimerEntry.of("1500").digits)
        assertEquals("1500", TimerEntry.of("001500").digits)
        assertEquals("1234", TimerEntry.of("12x34").digits)
        assertEquals("123456", TimerEntry.of("1234567").digits)
        assertEquals("", TimerEntry.of("000").digits)
    }

    @Test
    fun theQuickChipsSetTheLengthsTheyName() {
        assertEquals(listOf("1 min", "3 min", "5 min", "10 min", "25 min", "1 hour"), QuickTimers.map { it.name })
        assertEquals(
            listOf(1.minutes, 3.minutes, 5.minutes, 10.minutes, 25.minutes, 1.hours),
            QuickTimers.map { it.entry.duration },
        )
        assertEquals("Timer for 25 min", QuickTimers.first { it.name == "25 min" }.entry.label)
    }

    @Test
    fun typingGoesOnFromAChip() {
        val tenMinutes = QuickTimers.first { it.name == "10 min" }.entry

        assertEquals("1000", tenMinutes.digits)
        assertEquals("00:10:00", tenMinutes.readout())
        assertEquals(1.hours, tenMinutes.type("0").duration)
    }
}
