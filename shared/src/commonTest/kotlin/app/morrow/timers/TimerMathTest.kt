package app.morrow.timers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val noon = Instant.parse("2026-10-12T12:00:00Z")

class TimerMathTest {

    private val pasta = newTimer(10.minutes, "  Take the pasta off the hob ", noon)

    @Test
    fun aNewTimerRunsFromNowWithItsNoteTrimmed() {
        assertEquals(Timer(0, 10.minutes, noon + 10.minutes, null, "Take the pasta off the hob", noon), pasta)
        assertTrue(pasta.running)
    }

    @Test
    fun aTimerNeedsSomeTimeOnIt() {
        assertFailsWith<IllegalArgumentException> { newTimer(Duration.ZERO, "", noon) }
    }

    @Test
    fun aTimerIsEitherRunningOrPaused() {
        assertFailsWith<IllegalArgumentException> { Timer(1, 1.minutes, noon, 1.minutes, "", noon) }
        assertFailsWith<IllegalArgumentException> { Timer(1, 1.minutes, null, null, "", noon) }
    }

    @Test
    fun remainingCountsDownAndStopsAtZero() {
        assertEquals(10.minutes, pasta.remaining(noon))
        assertEquals(5.minutes + 48.seconds, pasta.remaining(noon + 4.minutes + 12.seconds))
        assertEquals(Duration.ZERO, pasta.remaining(noon + 10.minutes))
        assertEquals(Duration.ZERO, pasta.remaining(noon + 1.hours))
    }

    @Test
    fun aTimerEndsAtItsEndAndNotBefore() {
        assertFalse(pasta.hasEnded(noon + 10.minutes - 1.milliseconds))
        assertTrue(pasta.hasEnded(noon + 10.minutes))
        assertFalse(pasta.paused(noon + 1.minutes).hasEnded(noon + 1.hours))
    }

    @Test
    fun progressRunsFromZeroToOne() {
        assertEquals(0f, pasta.progress(noon))
        assertEquals(0.25f, pasta.progress(noon + 150.seconds))
        assertEquals(1f, pasta.progress(noon + 11.minutes))
    }

    @Test
    fun pausingHoldsWhatsLeftWhileTimePasses() {
        val paused = pasta.paused(noon + 4.minutes)

        assertEquals(Timer(0, 10.minutes, null, 6.minutes, pasta.note, noon), paused)
        assertEquals(6.minutes, paused.remaining(noon + 3.hours))
        assertEquals(paused, paused.paused(noon + 5.minutes))
    }

    @Test
    fun resumingRunsOnFromWhatWasLeft() {
        val resumed = pasta.paused(noon + 4.minutes).resumed(noon + 1.hours)

        assertEquals(noon + 1.hours + 6.minutes, resumed.endsAt)
        assertEquals(null, resumed.remaining)
        assertEquals(10.minutes, resumed.duration)
        assertEquals(resumed, resumed.resumed(noon + 2.hours))
    }

    @Test
    fun aMinuteMoreGoesOnTheTimeLeftAndTheTotal() {
        val running = pasta.plusMinute(noon + 4.minutes)
        assertEquals(noon + 11.minutes, running.endsAt)
        assertEquals(11.minutes, running.duration)

        val paused = pasta.paused(noon + 4.minutes).plusMinute(noon + 1.hours)
        assertEquals(7.minutes, paused.remaining)
        assertEquals(11.minutes, paused.duration)
    }

    @Test
    fun aMinuteMoreOnATimerThatHasEndedCountsFromNow() {
        val late = pasta.plusMinute(noon + 13.minutes)
        assertEquals(noon + 14.minutes, late.endsAt)
        assertEquals(11.minutes, late.duration)
    }

    @Test
    fun snoozingRingsAgainInAMinuteWithTheSameNote() {
        val snoozed = pasta.copy(id = 4).snoozed(noon + 10.minutes + 20.seconds)
        assertEquals(Timer(4, 1.minutes, noon + 11.minutes + 20.seconds, null, pasta.note, noon), snoozed)
    }

    @Test
    fun afterARebootRunningAndPausedTimersCarryOn() {
        val later = timer(1, endsAt = noon + 5.minutes)
        val paused = pasta.copy(id = 2).paused(noon - 1.minutes)

        assertEquals(Restored(keep = listOf(later, paused), ring = emptyList(), missed = emptyList()), restore(listOf(later, paused), noon))
    }

    @Test
    fun aTimerThatEndedInTheLastTenMinutesRings() {
        val justNow = timer(1, endsAt = noon - 1.seconds)
        val tenMinutesAgo = timer(2, endsAt = noon - 10.minutes)
        val onTheDot = timer(3, endsAt = noon)

        val restored = restore(listOf(justNow, tenMinutesAgo, onTheDot), noon)

        assertEquals(listOf(tenMinutesAgo, justNow, onTheDot), restored.ring)
        assertEquals(emptyList(), restored.missed)
    }

    @Test
    fun aTimerThatEndedLongerAgoIsMissedNotDropped() {
        val justTooLate = timer(1, endsAt = noon - 10.minutes - 1.milliseconds)
        val hoursAgo = timer(2, endsAt = noon - 3.hours)
        val stillRinging = timer(3, endsAt = noon - 2.minutes)

        val restored = restore(listOf(justTooLate, hoursAgo, stillRinging), noon)

        assertEquals(listOf(justTooLate, hoursAgo), restored.missed)
        assertEquals(listOf(stillRinging), restored.ring)
        assertEquals(emptyList(), restored.keep)
    }

    private fun timer(id: Long, endsAt: Instant) = Timer(id, 10.minutes, endsAt, null, "", endsAt - 10.minutes)
}

class TimerTextTest {

    @Test
    fun totalsReadAsTheDesignWritesThem() {
        assertEquals("45 s", totalText(45.seconds))
        assertEquals("1 min", totalText(1.minutes))
        assertEquals("10 min", totalText(10.minutes))
        assertEquals("2 min", totalText(90.seconds))
        assertEquals("1 h", totalText(1.hours))
        assertEquals("1 h 30 min", totalText(90.minutes))
        assertEquals("2 h 5 min", totalText(2.hours + 5.minutes))
    }

    @Test
    fun roundingUpToAWholeHourCountsTheHour() {
        assertEquals("2 h", totalText(1.hours + 59.minutes + 40.seconds))
        assertEquals("1 h", totalText(59.minutes + 45.seconds))
        assertEquals("59 min", totalText(59.minutes + 20.seconds))
    }

    @Test
    fun theClockCountsDownInMinutesAndSecondsThenHours() {
        assertEquals("04:12", clockText(4.minutes + 12.seconds))
        assertEquals("00:01", clockText(300.milliseconds))
        assertEquals("00:00", clockText(Duration.ZERO))
        assertEquals("59:59", clockText(1.hours - 1.seconds))
        assertEquals("1:02:03", clockText(1.hours + 2.minutes + 3.seconds))
    }

    @Test
    fun aTimerIsCalledByItsNoteOrItsLength() {
        assertEquals("Laundry out", newTimer(45.minutes, "Laundry out", noon).name)
        assertEquals("45 min timer", newTimer(45.minutes, "   ", noon).name)
    }
}
