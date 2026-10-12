package app.morrow.platform

import app.morrow.timers.Timer

/** Why a timer ended without being heard through to Stop or "+1 minute". */
enum class MissedReason {
    /** It ended while the phone was off, too long before it came back on to ring. */
    PHONE_OFF,

    /** It ended too long ago while nothing could ring it, as after Morrow was force-stopped. */
    TOO_LATE,

    /** It rang until the safety stop. */
    NOT_STOPPED,
}

/** The timers' own notifications. The one shown while timers ring belongs to the platform's ringing code instead. */
interface TimerNotifier {
    /** Shows or updates the ongoing notification of a running or paused timer: a countdown, or what's left. */
    fun show(timer: Timer)

    /** [timer] ended while an alarm was ringing, and rings once the alarm's Morning screen is done. */
    fun showWaiting(timer: Timer)

    /** Leaves a notification, in place of its ongoing one, that [timer] ended without being heard, and why. */
    fun showMissed(timer: Timer, reason: MissedReason)

    /** Paused [timer] wasn't resumed, as the phone wouldn't ring it on time: shows it paused, saying what to allow. */
    fun showCantResume(timer: Timer) = show(timer)

    fun hide(timerId: Long)
}
