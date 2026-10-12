package app.morrow.timers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A timer that ended while nothing could ring it, as when the phone was off, still rings if it ended this recently. */
val RING_GRACE: Duration = 10.minutes

/** A new running timer of [duration] from [now]. Its id is 0 until it's saved. */
fun newTimer(duration: Duration, note: String, now: Instant): Timer {
    require(duration.isPositive()) { "A timer needs some time on it" }
    return Timer(id = 0, duration = duration, endsAt = now + duration, remaining = null, note = note.trim(), createdAt = now)
}

/** What's left at [now]: counting down while running, held while paused, and never below zero. */
fun Timer.remaining(now: Instant): Duration =
    endsAt?.let { (it - now).coerceAtLeast(Duration.ZERO) } ?: requireNotNull(remaining)

/** True once a running timer's end has come. A paused timer never ends. */
fun Timer.hasEnded(now: Instant): Boolean = endsAt != null && endsAt <= now

/** How far it has run, from 0 when set to 1 when it ends. */
fun Timer.progress(now: Instant): Float = (1.0 - remaining(now) / duration).toFloat().coerceIn(0f, 1f)

/** Holds what's left at [now]. A paused timer stays as it is. */
fun Timer.paused(now: Instant): Timer = if (endsAt == null) this else copy(endsAt = null, remaining = remaining(now))

/** Runs on from [now] with what was left. A running timer stays as it is. */
fun Timer.resumed(now: Instant): Timer = remaining?.let { copy(endsAt = now + it, remaining = null) } ?: this

/**
 * One more minute, on the time left and on the total, like the timer card's "+1 min". A timer that has already ended
 * runs on for a minute from [now].
 */
fun Timer.plusMinute(now: Instant): Timer = when (endsAt) {
    null -> copy(duration = duration + 1.minutes, remaining = requireNotNull(remaining) + 1.minutes)
    else -> copy(duration = duration + 1.minutes, endsAt = maxOf(endsAt, now) + 1.minutes)
}

/** The Timer done screen's "+1 minute": rings again a minute from [now], as a 1-minute timer with the same note. */
fun Timer.snoozed(now: Instant): Timer = copy(duration = 1.minutes, endsAt = now + 1.minutes, remaining = null)

/** What becomes of stored timers that nothing is ringing: after a reboot, or when the app starts afresh. */
data class Restored(
    /** Still running or paused: the running ones are scheduled again, and all of them get their notification back. */
    val keep: List<Timer>,
    /** Ended within [RING_GRACE]: they ring now, once, in the order they ended. */
    val ring: List<Timer>,
    /** Ended longer ago: too late to ring, so each leaves a notification instead and is removed. */
    val missed: List<Timer>,
)

/** Sorts [timers] by whether they can still ring at [now]. None of them is ever just dropped. */
fun restore(timers: List<Timer>, now: Instant): Restored {
    val (ended, keep) = timers.partition { it.hasEnded(now) }
    val (ring, missed) = ended.partition { now - requireNotNull(it.endsAt) <= RING_GRACE }
    return Restored(keep, ring.sortedBy { it.endsAt }, missed)
}
