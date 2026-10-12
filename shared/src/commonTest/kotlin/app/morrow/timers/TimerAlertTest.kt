package app.morrow.timers

import app.morrow.alarms.FakeClock
import app.morrow.data.FakeTimerDao
import app.morrow.platform.FakeTimerNotifier
import app.morrow.platform.FakeTimerNotifier.Ongoing
import app.morrow.platform.TimerScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    /** Ringing on their alerts, which also count as [counting]. */
    val alerting = mutableSetOf<Long>()
    val refused = mutableSetOf<Long>()

    /** What the system says, as the bridge reports it. */
    fun system(vararg opening: Long) = SystemTimers(
        held = counting.keys + paused.keys + opening.toSet(),
        counting = counting.keys - alerting,
        alerting = alerting.toSet(),
        refused = refused.toSet(),
    )
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

        // Not at once, in case it was the alert's "Open" and that is on its way.
        assertTrue(timers.catchUp(alarmKit.system()))
        assertNotNull(repository.get(id))
        clock.now += TimerCoordinator.ALERT_HANDOVER
        assertFalse(timers.catchUp(alarmKit.system()))

        assertNull(repository.get(id))
        assertNull(notifier.shown[id])
        assertEquals(emptyList(), timers.ringing.value)
    }

    @Test
    fun aTimerOpenedFromItsAlertJustAfterTheSystemLetItGoStillRings() = runTest {
        val id = timers.start(10.minutes, "Pasta")!!
        clock.now = noon + 12.minutes
        // The system takes the alert down as "Open" is tapped, and Morrow catches up before the button's code runs.
        alarmKit.cancel(id)
        timers.catchUp(alarmKit.system())
        clock.now += 2.seconds

        assertEquals(listOf(id), timers.openFromAlert(setOf(id)).map { it.id })
        clock.now += TimerCoordinator.ALERT_HANDOVER
        timers.catchUp(alarmKit.system(id))
        assertNotNull(repository.get(id))
    }

    @Test
    fun aTimerTheSystemRefusedIsNotTakenAsStoppedOnItsAlert() = runTest {
        val id = timers.start(10.minutes, "Pasta")!!
        // The phone had too many alarms, so it never counted this one down.
        alarmKit.cancel(id)
        alarmKit.refused += id
        clock.now = noon + 12.minutes

        repeat(2) {
            timers.catchUp(alarmKit.system())
            clock.now += TimerCoordinator.ALERT_HANDOVER
        }

        assertNotNull(repository.get(id), "its card shows it ended until it's stopped")
    }

    @Test
    fun aRefusedTimerStillRunningIsHandedOverAgain() = runTest {
        val id = timers.start(10.minutes)!!
        alarmKit.cancel(id)
        alarmKit.refused += id
        clock.now = noon + 3.minutes

        timers.catchUp(alarmKit.system())

        assertEquals(mapOf(id to noon + 10.minutes), alarmKit.counting)
    }

    @Test
    fun aPausedTimerResumedFromTheSystemsOwnViewRunsOnInMorrow() = runTest {
        val id = timers.start(10.minutes)!!
        clock.now = noon + 4.minutes
        timers.pause(id)
        // Resume on the system's view, which Morrow doesn't hear of.
        alarmKit.paused.remove(id)
        alarmKit.counting[id] = noon + 20.minutes
        clock.now = noon + 15.minutes

        timers.catchUp(alarmKit.system())

        val resumed = repository.get(id)!!
        assertEquals(noon + 21.minutes, resumed.endsAt)
        assertEquals(mapOf(id to noon + 21.minutes), alarmKit.counting, "the system is handed Morrow's end, so the two agree")
    }

    @Test
    fun aPausedTimerRingingOnItsAlertHasEndedAndIsNotHandedBackPaused() = runTest {
        val id = timers.start(10.minutes)!!
        clock.now = noon + 4.minutes
        timers.pause(id)
        alarmKit.paused.remove(id)
        alarmKit.counting[id] = noon + 10.minutes
        alarmKit.alerting += id
        clock.now = noon + 11.minutes

        timers.catchUp(alarmKit.system())
        // Then stopped on its alert.
        alarmKit.cancel(id)
        alarmKit.alerting.clear()
        timers.catchUp(alarmKit.system())
        clock.now += TimerCoordinator.ALERT_HANDOVER
        timers.catchUp(alarmKit.system())

        assertNull(repository.get(id))
        assertEquals(emptyMap(), alarmKit.paused)
    }

    @Test
    fun aPausedTimerOpenedFromItsAlertRings() = runTest {
        val id = timers.start(10.minutes, "Pasta")!!
        clock.now = noon + 4.minutes
        timers.pause(id)
        clock.now = noon + 20.minutes

        val ringing = timers.openFromAlert(setOf(id))

        assertEquals(listOf(id), ringing.map { it.id })
        assertEquals(noon + 20.minutes, ringing.single().endsAt)
    }

    @Test
    fun aTimerStillRingingOnItsAlertIsLeftAlone() = runTest {
        val id = timers.start(10.minutes)!!
        clock.now = noon + 12.minutes

        timers.catchUp(alarmKit.system())

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

        timers.catchUp(alarmKit.system())

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
        timers.catchUp(alarmKit.system())
        assertNotNull(repository.get(id))
    }

    @Test
    fun onlyTheOpenedTimersStillStoredRing() = runTest {
        val opened = timers.start(10.minutes)!!
        val other = timers.start(10.minutes)!!
        val running = timers.start(1.hours)!!
        clock.now = noon + 10.minutes

        timers.openFromAlert(setOf(opened, 999))

        assertEquals(listOf(opened), timers.ringing.value.map { it.id })
        assertNotNull(repository.get(other))
        assertEquals(noon + 1.hours, repository.get(running)?.endsAt)
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
