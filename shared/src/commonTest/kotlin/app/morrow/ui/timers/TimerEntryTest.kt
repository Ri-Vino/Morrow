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

    private fun typed(vararg keys: String) = TimerEntry.of(keys.joinToString(""))

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
    fun digitsFillFromTheRight() {
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
    fun aChipShowsItsLength() {
        val tenMinutes = QuickTimers.first { it.name == "10 min" }.entry

        assertEquals("1000", tenMinutes.digits)
        assertEquals("00:10:00", tenMinutes.readout())
    }

    @Test
    fun aLengthBecomesItsDigits() {
        assertEquals("1500", TimerEntry.of(15.minutes).digits)
        assertEquals("10203", TimerEntry.of(1.hours + 2.minutes + 3.seconds).digits)
        assertEquals("", TimerEntry.of(Duration.ZERO).digits)
        assertEquals("995959", TimerEntry.of(200.hours).digits)
    }

    @Test
    fun theKeypadTypesDigitsInFromTheRight() {
        val keys = listOf(1, 5, 0, 0).fold(TimerEntry()) { entry, digit -> entry.typed(digit) }
        assertEquals("00:15:00", keys.readout())
        assertEquals(2, keys.waiting)
        // Zeros before anything else count for nothing, and a seventh digit doesn't fit.
        assertEquals(TimerEntry(), TimerEntry().typed(0).typed(0))
        assertEquals("123456", TimerEntry("123456").typed(7).digits)
        assertEquals(6, TimerEntry().waiting)
    }

    @Test
    fun deletingTakesOffTheLastDigitTyped() {
        assertEquals("00:01:50", TimerEntry("1500").deleted().readout())
        assertEquals(TimerEntry(), TimerEntry("5").deleted())
        assertEquals(TimerEntry(), TimerEntry().deleted())
    }

    @Test
    fun windingPipRoundTheDialSetsMinutesAndALapIsAnHour() {
        assertEquals(15, windDial(0, 0.25f))
        assertEquals(30, windDial(15, 0.5f))
        // Carrying on past the top winds on an hour, and going back past it unwinds one.
        assertEquals(62, windDial(58, 2 / 60f))
        assertEquals(58, windDial(62, 58 / 60f))
        // It won't go below nothing, or past twelve hours.
        assertEquals(0, windDial(0, 59 / 60f))
        assertEquals(MAX_DIAL_MINUTES, windDial(MAX_DIAL_MINUTES, 1 / 60f))
    }
}
