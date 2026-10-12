package app.morrow.timers

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A countdown with an optional note for Pip to read out when it ends. It's running while [endsAt] is set and paused
 * while [remaining] is; exactly one of the two is. A timer that has ended keeps its [endsAt] until it's stopped.
 */
data class Timer(
    val id: Long,
    /** How long it was set for, plus any minutes added since. */
    val duration: Duration,
    /**
     * When a running timer ends, as a moment on the phone's clock; null while paused. Changing the clock by hand moves
     * the end with it, so a timer with 20 minutes left rings at once when the clock is put an hour ahead, as ending
     * then (see [TimerCoordinator.rescheduleAll]). That's accepted: time since boot would keep the length, but it
     * starts again from zero after a reboot.
     */
    val endsAt: Instant?,
    /** What was left when it was paused. Null while running. */
    val remaining: Duration?,
    /** Blank when there's no note. */
    val note: String,
    val createdAt: Instant,
) {
    init {
        require((endsAt == null) != (remaining == null)) { "A timer is either running or paused" }
    }

    val running: Boolean get() = endsAt != null
}
