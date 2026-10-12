package app.morrow.alarm

import kotlin.test.Test
import kotlin.test.assertEquals

/** What RingService does once a timer has been found, in each state the service can be in by then. */
class AfterFindingTimersTest {

    @Test
    fun theFirstTimerToEndStartsFollowingTheRingingTimers() {
        assertEquals(AfterFinding.FOLLOW, afterFindingTimers(alarm = false, bell = true, ringing = true, following = false))
    }

    @Test
    fun aTimerJoiningOthersThatRingChangesNothing() {
        assertEquals(AfterFinding.WAIT, afterFindingTimers(alarm = false, bell = true, ringing = true, following = true))
    }

    @Test
    fun aTimerFoundOnceTheBellHasStoppedRingsItAgain() {
        // Timer A was stopped while B's alarm was being handled, which ended the Bell; or the Morning screen went
        // while B was being found, so B rings rather than waits.
        assertEquals(AfterFinding.RING, afterFindingTimers(alarm = false, bell = false, ringing = true, following = false))
    }

    @Test
    fun timersWaitBehindAnAlarm() {
        assertEquals(AfterFinding.WAIT, afterFindingTimers(alarm = true, bell = false, ringing = false, following = false))
        assertEquals(AfterFinding.WAIT, afterFindingTimers(alarm = true, bell = false, ringing = true, following = false))
    }

    @Test
    fun withNothingRingingTheServiceStops() {
        assertEquals(AfterFinding.STOP, afterFindingTimers(alarm = false, bell = true, ringing = false, following = true))
        assertEquals(AfterFinding.STOP, afterFindingTimers(alarm = false, bell = false, ringing = false, following = false))
    }
}
