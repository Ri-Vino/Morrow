package app.morrow.timers

import app.morrow.alarms.FakeClock
import app.morrow.data.FakeTimerDao
import app.morrow.platform.FakeTimerNotifier
import app.morrow.platform.FakeTimerNotifier.Ongoing
import app.morrow.platform.TimerScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val noon = Instant.parse("2026-10-12T12:00:00Z")

/** Plays AlarmKit's part: it keeps a paused timer, paused, rather than forgetting it. */
private class AlarmKitTimers : TimerScheduler {
    val counting = mutableMapOf<Long, Instant>()
    val paused = mutableMapOf<Long, Duration>()

    override fun canRingOnTime(): Boolean = true

    override fun schedule(timer: Timer) {
        paused.remove(timer.id)
        counting[timer.id] = requireNotNull(timer.endsAt)
    }

    override fun pause(timer: Timer) {
        counting.remove(timer.id)
        paused[timer.id] = requireNotNull(timer.remaining)
    }

    override fun cancel(timerId: Long) {
        counting.remove(timerId)
        paused.remove(timerId)
    }

    /** What the system holds, as the bridge's scheduledIds() reports it. */
    fun held(): Set<Long> = counting.keys + paused.keys
}

/** iOS, where AlarmKit rings timers itself and Morrow only hears of one when it's opened from its alert. */
class TimerAlertTest {

    private val repository = TimerRepository(FakeTimerDao())
    private val alarmKit = AlarmKitTimers()
    private val notifier = FakeTimerNotifier()
    private val clock = FakeClock(noon)
    private val timers = TimerCoordinator(repository, alarmKit, notifier, clock)

    @Test
    fun pausingHandsOverWhatsLeftAndResumingCountsDownAgain() = runTest {
        val id = timers.start(10.minutes)!!
        clock.now = noon + 4.minutes

        timers.pause(id)
        assertEquals(mapOf(id to 6.minutes), alarmKit.paused)
        assertEquals(emptyMap(), alarmKit.counting)

        timers.addMinute(id)
        assertEquals(mapOf(id to 7.minutes), alarmKit.paused)

        clock.now = noon + 1.hours
        timers.resume(id)
        assertEquals(mapOf(id to noon + 1.hours + 7.minutes), alarmKit.counting)
        assertEquals(emptyMap(), alarmKit.paused)
    }

    @Test
    fun aTimerStoppedOnItsAlertIsRemovedWhenMorrowCatchesUp() = runTest {
        val id = timers.start(10.minutes, "Pasta")!!
        clock.now = noon + 12.minutes
        // Stop on the alert: AlarmKit lets it go without telling Morrow.
        alarmKit.cancel(id)

        timers.catchUp(alarmKit.held())

        assertNull(repository.get(id))
        assertNull(notifier.shown[id])
        assertEquals(emptyList(), timers.ringing.value)
    }

    @Test
    fun aTimerStillRingingOnItsAlertIsLeftAlone() = runTest {
        val id = timers.start(10.minutes)!!
        clock.now = noon + 12.minutes

        timers.catchUp(alarmKit.held())

        assertNotNull(repository.get(id))
        assertEquals(mapOf(id to noon + 10.minutes), alarmKit.counting)
    }

    @Test
    fun timersTheSystemLostAreHandedOverAgain() = runTest {
        val running = timers.start(10.minutes)!!
        val paused = timers.start(1.hours)!!
        clock.now = noon + 3.minutes
        timers.pause(paused)
        alarmKit.counting.clear()
        alarmKit.paused.clear()

        timers.catchUp(alarmKit.held())

        assertEquals(mapOf(running to noon + 10.minutes), alarmKit.counting)
        assertEquals(mapOf(paused to 57.minutes), alarmKit.paused)
        assertEquals(Ongoing(repository.get(running)!!), notifier.shown[running])
    }

    @Test
    fun aTimerOpenedFromItsAlertRingsHoweverLongAgoItEnded() = runTest {
        val id = timers.start(10.minutes, "Pasta")!!
        clock.now = noon + 45.minutes

        val ringing = timers.openFromAlert(setOf(id))

        assertEquals(listOf(id), ringing.map { it.id })
        assertEquals(ringing, timers.ringing.value)
        // Ringing, it isn't mistaken for one stopped on its alert.
        alarmKit.cancel(id)
        timers.catchUp(alarmKit.held())
        assertNotNull(repository.get(id))
    }

    @Test
    fun onlyTheOpenedTimersThatHaveEndedRing() = runTest {
        val opened = timers.start(10.minutes)!!
        val other = timers.start(10.minutes)!!
        val running = timers.start(1.hours)!!
        clock.now = noon + 10.minutes

        timers.openFromAlert(setOf(opened, running, 999))

        assertEquals(listOf(opened), timers.ringing.value.map { it.id })
        assertNotNull(repository.get(other))
    }

    @Test
    fun theDoneScreenStopsOrSnoozesThem() = runTest {
        val pasta = timers.start(10.minutes, "Pasta")!!
        val eggs = timers.start(10.minutes, "Eggs")!!
        clock.now = noon + 11.minutes
        timers.openFromAlert(setOf(pasta))
        timers.stopRinging()
        assertNull(repository.get(pasta))

        timers.openFromAlert(setOf(eggs))
        timers.snoozeRinging()

        assertEquals(emptyList(), timers.ringing.value)
        assertEquals(Timer(eggs, 1.minutes, noon + 12.minutes, null, "Eggs", noon), repository.get(eggs))
        assertEquals(mapOf(eggs to noon + 12.minutes), alarmKit.counting)
    }
}
