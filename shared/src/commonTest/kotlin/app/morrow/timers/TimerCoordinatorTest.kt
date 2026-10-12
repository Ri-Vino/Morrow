package app.morrow.timers

import app.morrow.alarms.FakeClock
import app.morrow.data.FakeTimerDao
import app.morrow.platform.FakeTimerNotifier
import app.morrow.platform.FakeTimerNotifier.Missed
import app.morrow.platform.FakeTimerNotifier.Ongoing
import app.morrow.platform.FakeTimerNotifier.Waiting
import app.morrow.platform.FakeTimerScheduler
import app.morrow.platform.MissedReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val noon = Instant.parse("2026-10-12T12:00:00Z")

class TimerCoordinatorTest {

    private val dao = FakeTimerDao()
    private val repository = TimerRepository(dao)
    private val scheduler = FakeTimerScheduler()
    private val notifier = FakeTimerNotifier()
    private val clock = FakeClock(noon)
    private val coordinator = TimerCoordinator(repository, scheduler, notifier, clock)

    private suspend fun stored(id: Long) = repository.get(id)

    @Test
    fun startingSchedulesItsEndAndShowsItsNotification() = runTest {
        val id = assertNotNull(coordinator.start(10.minutes, "Pasta"))

        val timer = Timer(id, 10.minutes, noon + 10.minutes, null, "Pasta", noon)
        assertEquals(timer, stored(id))
        assertEquals(mapOf(id to noon + 10.minutes), scheduler.scheduled)
        assertEquals(Ongoing(timer), notifier.shown[id])
        assertEquals(listOf(timer), coordinator.timers.first())
    }

    @Test
    fun withoutExactAlarmsNoTimerStarts() = runTest {
        scheduler.allowed = false

        assertNull(coordinator.start(10.minutes))

        assertEquals(emptyList(), repository.getAll())
        assertEquals(emptyMap(), scheduler.scheduled)
        assertEquals(emptyMap(), notifier.shown)
    }

    @Test
    fun pausingCancelsTheAlarmAndKeepsWhatsLeft() = runTest {
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 4.minutes

        coordinator.pause(id)

        val paused = Timer(id, 10.minutes, null, 6.minutes, "", noon)
        assertEquals(paused, stored(id))
        assertEquals(emptyMap(), scheduler.scheduled)
        assertEquals(Ongoing(paused), notifier.shown[id])
    }

    @Test
    fun resumingSchedulesTheNewEnd() = runTest {
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 4.minutes
        coordinator.pause(id)
        clock.now = noon + 1.hours

        assertTrue(coordinator.resume(id))

        assertEquals(noon + 1.hours + 6.minutes, stored(id)?.endsAt)
        assertEquals(mapOf(id to noon + 1.hours + 6.minutes), scheduler.scheduled)
    }

    @Test
    fun withoutExactAlarmsAPausedTimerStaysPaused() = runTest {
        val id = coordinator.start(10.minutes)!!
        coordinator.pause(id)
        scheduler.allowed = false

        assertFalse(coordinator.resume(id))

        assertEquals(10.minutes, stored(id)?.remaining)
        assertEquals(emptyMap(), scheduler.scheduled)
    }

    @Test
    fun aMinuteMoreMovesTheEnd() = runTest {
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 3.minutes

        coordinator.addMinute(id)

        assertEquals(Timer(id, 11.minutes, noon + 11.minutes, null, "", noon), stored(id))
        assertEquals(noon + 11.minutes, scheduler.scheduled[id])
    }

    @Test
    fun stoppingRemovesTheTimerItsAlarmAndItsNotification() = runTest {
        val id = coordinator.start(10.minutes)!!

        coordinator.stop(id)

        assertNull(stored(id))
        assertEquals(emptyMap(), scheduler.scheduled)
        assertEquals(emptyMap(), notifier.shown)
    }

    @Test
    fun anEndedTimerRingsInPlaceOfItsNotification() = runTest {
        val id = coordinator.start(10.minutes, "Pasta")!!
        clock.now = noon + 10.minutes

        val ringing = coordinator.ringEnded(behindAlarm = false)

        assertEquals(listOf(id), ringing.map { it.id })
        assertEquals(ringing, coordinator.ringing.value)
        assertNull(notifier.shown[id])
        assertNotNull(stored(id))
    }

    @Test
    fun runningTimersDoNotRingEarly() = runTest {
        coordinator.start(10.minutes)!!
        clock.now = noon + 9.minutes

        assertEquals(emptyList(), coordinator.ringEnded(behindAlarm = false))
    }

    @Test
    fun timersEndingTogetherRingTogetherOnce() = runTest {
        val pasta = coordinator.start(10.minutes, "Pasta")!!
        clock.now = noon + 2.minutes
        val eggs = coordinator.start(8.minutes, "Eggs")!!
        clock.now = noon + 10.minutes

        coordinator.ringEnded(behindAlarm = false)
        // Each timer's alarm goes off, so the second one asks again.
        coordinator.ringEnded(behindAlarm = false)

        assertEquals(listOf(pasta, eggs), coordinator.ringing.value.map { it.id })
    }

    @Test
    fun aTimerEndingWhileOthersRingJoinsThem() = runTest {
        val first = coordinator.start(1.minutes)!!
        val second = coordinator.start(2.minutes)!!
        clock.now = noon + 1.minutes
        coordinator.ringEnded(behindAlarm = false)
        clock.now = noon + 2.minutes

        coordinator.ringEnded(behindAlarm = false)

        assertEquals(listOf(first, second), coordinator.ringing.value.map { it.id })
    }

    @Test
    fun stopStopsEveryRingingTimer() = runTest {
        val pasta = coordinator.start(10.minutes)!!
        val eggs = coordinator.start(10.minutes)!!
        val later = coordinator.start(1.hours)!!
        clock.now = noon + 10.minutes
        coordinator.ringEnded(behindAlarm = false)

        coordinator.stopRinging()

        assertEquals(emptyList(), coordinator.ringing.value)
        assertEquals(listOf(later), repository.getAll().map { it.id })
        assertEquals(setOf(later), scheduler.scheduled.keys)
        assertNull(notifier.shown[pasta])
        assertNull(notifier.shown[eggs])
    }

    @Test
    fun aMinuteMoreFromTheDoneScreenRingsEachAgainInAMinute() = runTest {
        val id = coordinator.start(10.minutes, "Pasta")!!
        clock.now = noon + 10.minutes + 5.seconds
        coordinator.ringEnded(behindAlarm = false)

        coordinator.snoozeRinging()

        val again = Timer(id, 1.minutes, noon + 11.minutes + 5.seconds, null, "Pasta", noon)
        assertEquals(emptyList(), coordinator.ringing.value)
        assertEquals(again, stored(id))
        assertEquals(mapOf(id to again.endsAt!!), scheduler.scheduled)
        assertEquals(Ongoing(again), notifier.shown[id])
    }

    @Test
    fun stoppingOneRingingTimerLeavesTheOthersRinging() = runTest {
        val pasta = coordinator.start(10.minutes)!!
        val eggs = coordinator.start(10.minutes)!!
        clock.now = noon + 10.minutes
        coordinator.ringEnded(behindAlarm = false)

        coordinator.stop(pasta)

        assertEquals(listOf(eggs), coordinator.ringing.value.map { it.id })
    }

    @Test
    fun aRingingTimerGivenAMinuteRunsAgain() = runTest {
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 10.minutes
        coordinator.ringEnded(behindAlarm = false)

        coordinator.addMinute(id)

        assertEquals(emptyList(), coordinator.ringing.value)
        assertEquals(noon + 11.minutes, scheduler.scheduled[id])
    }

    @Test
    fun theSafetyStopLeavesANotificationForEachTimer() = runTest {
        val id = coordinator.start(10.minutes, "Pasta")!!
        clock.now = noon + 10.minutes
        val ringing = coordinator.ringEnded(behindAlarm = false).single()
        clock.now = noon + 20.minutes

        coordinator.timeOutRinging()

        assertEquals(emptyList(), coordinator.ringing.value)
        assertNull(stored(id))
        assertEquals(Missed(ringing, MissedReason.NOT_STOPPED), notifier.shown[id])
    }

    @Test
    fun aTimerEndingDuringAnAlarmWaitsWithItsNotification() = runTest {
        val id = coordinator.start(10.minutes, "Pasta")!!
        clock.now = noon + 10.minutes

        val ringing = coordinator.ringEnded(behindAlarm = true)

        assertEquals(emptyList(), ringing)
        val waiting = coordinator.waiting.value.single()
        assertEquals(id, waiting.id)
        assertEquals(Waiting(waiting), notifier.shown[id])
    }

    @Test
    fun waitingTimersRingOnceTheAlarmIsDone() = runTest {
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 10.minutes
        coordinator.ringEnded(behindAlarm = true)
        // The alarm's games and Morning screen take a while.
        clock.now = noon + 25.minutes
        scheduler.scheduled.clear()

        coordinator.releaseWaiting()
        // The OS rings it straight away, as its end has passed.
        assertEquals(mapOf(id to noon + 10.minutes), scheduler.scheduled)
        coordinator.ringEnded(behindAlarm = false)

        assertEquals(listOf(id), coordinator.ringing.value.map { it.id })
        assertEquals(emptyList(), coordinator.waiting.value)
        assertNull(notifier.shown[id])
    }

    @Test
    fun anAlarmOverRingingTimersMakesThemWait() = runTest {
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 10.minutes
        coordinator.ringEnded(behindAlarm = false)

        coordinator.holdRinging()

        assertEquals(emptyList(), coordinator.ringing.value)
        assertEquals(listOf(id), coordinator.waiting.value.map { it.id })
        assertTrue(notifier.shown[id] is Waiting)
    }

    @Test
    fun rescheduleAllLeavesRingingAndWaitingTimersAlone() = runTest {
        val ringing = coordinator.start(5.minutes)!!
        val waiting = coordinator.start(6.minutes)!!
        clock.now = noon + 5.minutes
        coordinator.ringEnded(behindAlarm = false)
        clock.now = noon + 6.minutes
        coordinator.ringEnded(behindAlarm = true)
        clock.now = noon + 1.hours
        scheduler.scheduled.clear()

        coordinator.rescheduleAll()

        assertEquals(emptyMap(), scheduler.scheduled)
        assertEquals(listOf(ringing), coordinator.ringing.value.map { it.id })
        assertEquals(listOf(waiting), coordinator.waiting.value.map { it.id })
        assertNotNull(stored(ringing))
        assertNotNull(stored(waiting))
    }

    @Test
    fun aTimerFoundLongAfterItEndedLeavesANotification() = runTest {
        // The phone slept through it, so its alarm comes late.
        val id = coordinator.start(10.minutes)!!
        clock.now = noon + 30.minutes

        assertEquals(emptyList(), coordinator.ringEnded(behindAlarm = false))

        assertNull(stored(id))
        assertEquals(MissedReason.TOO_LATE, (notifier.shown[id] as Missed).reason)
    }
}

/** Review focus 2: a timer that ends while the phone is off rings if it ended within 10 minutes, otherwise it posts a notification, and never vanishes. */
class TimerRebootTest {

    private val repository = TimerRepository(FakeTimerDao())
    private val clock = FakeClock(noon)

    /** The app's process as it was before the phone went off. */
    private val before = TimerCoordinator(repository, FakeTimerScheduler(), FakeTimerNotifier(), clock)

    /** And after it comes back on: a new process, with the OS schedule and every notification gone. */
    private val scheduler = FakeTimerScheduler()
    private val notifier = FakeTimerNotifier()
    private val after = TimerCoordinator(repository, scheduler, notifier, clock)

    @Test
    fun aRunningTimerIsScheduledAgainForTheSameEnd() = runTest {
        val id = before.start(1.hours, "Bread")!!
        clock.now = noon + 10.minutes

        after.rescheduleAll(afterReboot = true)

        assertEquals(mapOf(id to noon + 1.hours), scheduler.scheduled)
        assertEquals(Ongoing(repository.get(id)!!), notifier.shown[id])
    }

    @Test
    fun aPausedTimerGetsItsNotificationBackAndStaysPaused() = runTest {
        val id = before.start(1.hours)!!
        before.pause(id)
        clock.now = noon + 3.hours

        after.rescheduleAll(afterReboot = true)

        assertEquals(emptyMap(), scheduler.scheduled)
        assertEquals(1.hours, (notifier.shown[id] as Ongoing).timer.remaining)
    }

    @Test
    fun aTimerThatEndedWithinTenMinutesRingsOnBooting() = runTest {
        val id = before.start(10.minutes, "Pasta")!!
        // The phone was off from 12:05 and back on at 12:19, nine minutes after the timer ended.
        clock.now = noon + 19.minutes

        after.rescheduleAll(afterReboot = true)
        // Its end has passed, so the OS rings it straight away.
        assertEquals(mapOf(id to noon + 10.minutes), scheduler.scheduled)
        after.ringEnded(behindAlarm = false)

        assertEquals(listOf(id), after.ringing.value.map { it.id })
    }

    @Test
    fun aTimerThatEndedExactlyTenMinutesAgoStillRings() = runTest {
        val id = before.start(10.minutes)!!
        clock.now = noon + 20.minutes

        after.rescheduleAll(afterReboot = true)
        after.ringEnded(behindAlarm = false)

        assertEquals(listOf(id), after.ringing.value.map { it.id })
    }

    @Test
    fun aTimerThatEndedLongerAgoPostsANotificationInstead() = runTest {
        val id = before.start(10.minutes, "Pasta")!!
        val timer = repository.get(id)!!
        clock.now = noon + 20.minutes + 1.seconds

        after.rescheduleAll(afterReboot = true)

        assertEquals(Missed(timer, MissedReason.PHONE_OFF), notifier.shown[id])
        assertEquals(emptyMap(), scheduler.scheduled)
        assertNull(repository.get(id))
        assertEquals(emptyList(), after.ringEnded(behindAlarm = false))
    }

    @Test
    fun aTimerThatWasRingingWhenThePhoneWentOffRingsAgainIfRecent() = runTest {
        val id = before.start(10.minutes)!!
        clock.now = noon + 10.minutes
        before.ringEnded(behindAlarm = false)
        clock.now = noon + 14.minutes

        after.rescheduleAll(afterReboot = true)

        assertEquals(mapOf(id to noon + 10.minutes), scheduler.scheduled)
    }

    @Test
    fun aTimerWaitingBehindAnAlarmWhenTheAppDiesLeavesANotification() = runTest {
        val id = before.start(10.minutes)!!
        clock.now = noon + 10.minutes
        before.ringEnded(behindAlarm = true)
        clock.now = noon + 40.minutes

        after.rescheduleAll()

        assertEquals(MissedReason.TOO_LATE, (notifier.shown[id] as Missed).reason)
    }

    @Test
    fun everyTimerRingsCarriesOnOrLeavesANotification() = runTest {
        val ids = listOf(2.minutes, 9.minutes, 25.minutes, 1.hours, 5.hours).map { before.start(it)!! }
        before.pause(ids.last())
        clock.now = noon + 30.minutes

        after.rescheduleAll(afterReboot = true)

        val accounted = scheduler.scheduled.keys + notifier.shown.keys
        assertEquals(ids.toSet(), accounted)
        assertEquals(setOf(ids[0], ids[1]), notifier.shown.filterValues { it is Missed }.keys)
        assertEquals(setOf(ids[2]), scheduler.scheduled.filterValues { it < clock.now }.keys)
        assertEquals(setOf(ids[2], ids[3]), scheduler.scheduled.keys)
    }
}
