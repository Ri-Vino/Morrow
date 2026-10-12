package app.morrow.timers

import app.morrow.platform.MissedReason
import app.morrow.platform.TimerNotifier
import app.morrow.platform.TimerScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Keeps the OS schedule and the timers' notifications in step with the stored timers, and follows the ones that have
 * ended until they're stopped: [ringing], or [waiting] behind an alarm.
 *
 * Which timers are ringing or waiting is kept only in memory, since it lasts only as long as the app's process does.
 * A stored timer that has ended but that this process isn't ringing or holding was missed by a reboot or a force stop,
 * and [rescheduleAll] or [ringEnded] takes care of it.
 */
class TimerCoordinator(
    private val repository: TimerRepository,
    private val scheduler: TimerScheduler,
    private val notifier: TimerNotifier,
    private val clock: Clock = Clock.System,
) {
    private val lock = Mutex()
    private val ringingState = MutableStateFlow<List<Timer>>(emptyList())
    private val waitingState = MutableStateFlow<List<Timer>>(emptyList())

    /** Every timer, oldest first: running, paused, and ended ones until they're stopped. */
    val timers: Flow<List<Timer>> = repository.timers

    /**
     * The timers ringing now, in the order they ended. The Timer done screen shows them and Pip reads their notes;
     * the screen closes, and the sound stops, when this empties.
     */
    val ringing: StateFlow<List<Timer>> = ringingState.asStateFlow()

    /** Timers that ended while an alarm was ringing, in the order they ended. They ring after it; see [releaseWaiting]. */
    val waiting: StateFlow<List<Timer>> = waitingState.asStateFlow()

    /** Starts a timer. Returns its id, or null without starting it when the phone wouldn't ring it on time. */
    suspend fun start(duration: Duration, note: String = ""): Long? {
        if (!scheduler.canRingOnTime()) return null
        return lock.withLock {
            val timer = newTimer(duration, note, clock.now())
            val saved = timer.copy(id = repository.save(timer))
            scheduler.schedule(saved)
            notifier.show(saved)
            saved.id
        }
    }

    /** Holds what's left. A timer that has already ended stays ringing; stop it or give it a minute instead. */
    suspend fun pause(id: Long) = change(id) { timer, now -> if (timer.hasEnded(now)) timer else timer.paused(now) }

    /** Runs a paused timer on. Returns false, leaving it paused, when the phone wouldn't ring it on time. */
    suspend fun resume(id: Long): Boolean {
        if (!scheduler.canRingOnTime()) return false
        change(id) { timer, now -> timer.resumed(now) }
        return true
    }

    /** Adds a minute to what's left and to the total. A timer that's ringing or waiting runs on for a minute instead. */
    suspend fun addMinute(id: Long) = change(id) { timer, now -> timer.plusMinute(now) }

    /** Stops [id] and removes it, whether it's running, paused, ringing or waiting. */
    suspend fun stop(id: Long) = lock.withLock {
        try {
            scheduler.cancel(id)
            notifier.hide(id)
            repository.delete(id)
        } finally {
            forget(setOf(id))
        }
    }

    /**
     * For when a timer's alarm goes off: every timer that has ended joins [ringing], or [waiting] while [behindAlarm].
     * Joining [ringing] also brings in the waiting timers, as nothing is ringing ahead of them any more. A timer found
     * more than [RING_GRACE] after it ended, which nothing had been ringing, leaves a notification instead.
     * Returns [ringing].
     */
    suspend fun ringEnded(behindAlarm: Boolean): List<Timer> = lock.withLock {
        val held = held()
        val found = restore(repository.getAll().filterNot { it.id in held }, clock.now())
        miss(found.missed, MissedReason.TOO_LATE)
        if (behindAlarm) {
            found.ring.forEach(notifier::showWaiting)
            waitingState.value = (waitingState.value + found.ring).sortedBy { it.endsAt }
        } else {
            val joining = waitingState.value + found.ring
            joining.forEach { notifier.hide(it.id) }
            waitingState.value = emptyList()
            ringingState.value = (ringingState.value + joining).sortedBy { it.endsAt }
        }
        ringingState.value
    }

    /** An alarm went off while timers were ringing. It takes priority, so they wait for it. */
    suspend fun holdRinging() = lock.withLock {
        val holding = ringingState.value
        ringingState.value = emptyList()
        holding.forEach(notifier::showWaiting)
        waitingState.value = (waitingState.value + holding).sortedBy { it.endsAt }
    }

    /**
     * The alarm is done: its Morning screen has gone, or it stopped by itself and no Morning screen follows. The
     * waiting timers are handed to the OS again at their ends, which have passed, so it rings them straight away and
     * [ringEnded] takes them in.
     */
    fun releaseWaiting() {
        waitingState.value.forEach(scheduler::schedule)
    }

    /** The Timer done screen's Stop: stops every ringing timer. */
    suspend fun stopRinging() = lock.withLock {
        val stopping = ringingState.value
        try {
            stopping.forEach {
                scheduler.cancel(it.id)
                notifier.hide(it.id)
                repository.delete(it.id)
            }
        } finally {
            forget(stopping.map { it.id }.toSet())
        }
    }

    /** The Timer done screen's "+1 minute": every ringing timer rings again in a minute, as a 1-minute timer. */
    suspend fun snoozeRinging() = lock.withLock {
        val now = clock.now()
        val snoozing = ringingState.value
        try {
            snoozing.map { it.snoozed(now) }.forEach {
                repository.save(it)
                scheduler.schedule(it)
                notifier.show(it)
            }
        } finally {
            forget(snoozing.map { it.id }.toSet())
        }
    }

    /** The 10-minute safety stop: ringing timers no one stopped are removed, each leaving a notification that it rang. */
    suspend fun timeOutRinging() = lock.withLock {
        val unanswered = ringingState.value
        try {
            miss(unanswered, MissedReason.NOT_STOPPED)
        } finally {
            forget(unanswered.map { it.id }.toSet())
        }
    }

    /**
     * Puts the OS schedule and the notifications back: after a reboot ([afterReboot]), a clock or time zone change, an
     * app update or a permission change, and at every app start, since a force stop clears the schedule silently.
     * Timers this process is ringing or holding are left alone. Any other that has ended rings now if it ended within
     * [RING_GRACE], and otherwise leaves a notification. None is ever dropped without one.
     */
    suspend fun rescheduleAll(afterReboot: Boolean = false) = lock.withLock {
        val held = held()
        val found = restore(repository.getAll().filterNot { it.id in held }, clock.now())
        found.keep.forEach {
            if (it.running) scheduler.schedule(it)
            notifier.show(it)
        }
        found.ring.forEach(scheduler::schedule)
        miss(found.missed, if (afterReboot) MissedReason.PHONE_OFF else MissedReason.TOO_LATE)
    }

    private suspend fun change(id: Long, update: (Timer, Instant) -> Timer) = lock.withLock {
        val timer = repository.get(id) ?: return@withLock
        val changed = update(timer, clock.now())
        if (changed == timer) return@withLock
        repository.save(changed)
        // A ringing or waiting timer given another minute is running again.
        forget(setOf(id))
        if (changed.running) scheduler.schedule(changed) else scheduler.cancel(id)
        notifier.show(changed)
    }

    private suspend fun miss(timers: List<Timer>, reason: MissedReason) {
        timers.forEach {
            scheduler.cancel(it.id)
            notifier.showMissed(it, reason)
            repository.delete(it.id)
        }
    }

    private fun held(): Set<Long> = (ringingState.value + waitingState.value).map { it.id }.toSet()

    private fun forget(ids: Set<Long>) {
        ringingState.update { timers -> timers.filterNot { it.id in ids } }
        waitingState.update { timers -> timers.filterNot { it.id in ids } }
    }
}
