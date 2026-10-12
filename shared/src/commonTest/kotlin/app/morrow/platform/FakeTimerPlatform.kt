package app.morrow.platform

import app.morrow.timers.Timer
import kotlin.time.Instant

class FakeTimerScheduler : TimerScheduler {
    /** False plays the part of a phone without the exact-alarm permission. */
    var allowed = true
    val scheduled = mutableMapOf<Long, Instant>()

    override fun canRingOnTime(): Boolean = allowed

    override fun schedule(timer: Timer) {
        val endsAt = timer.endsAt
        if (endsAt == null) scheduled.remove(timer.id) else scheduled[timer.id] = endsAt
    }

    override fun cancel(timerId: Long) {
        scheduled.remove(timerId)
    }
}

class FakeTimerNotifier : TimerNotifier {
    sealed interface Shown {
        val timer: Timer
    }

    data class Ongoing(override val timer: Timer) : Shown

    data class Waiting(override val timer: Timer) : Shown

    data class Missed(override val timer: Timer, val reason: MissedReason) : Shown

    data class CantResume(override val timer: Timer) : Shown

    /** What each timer's notification says now, by timer id. */
    val shown = mutableMapOf<Long, Shown>()

    override fun show(timer: Timer) {
        shown[timer.id] = Ongoing(timer)
    }

    override fun showWaiting(timer: Timer) {
        shown[timer.id] = Waiting(timer)
    }

    override fun showMissed(timer: Timer, reason: MissedReason) {
        shown[timer.id] = Missed(timer, reason)
    }

    override fun showCantResume(timer: Timer) {
        shown[timer.id] = CantResume(timer)
    }

    override fun hide(timerId: Long) {
        shown.remove(timerId)
    }
}
