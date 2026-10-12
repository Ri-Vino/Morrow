package app.morrow.platform

import app.morrow.timers.Timer

/**
 * iOS can't ring timers yet, so none can start: Start asks for the alarm permission instead. AlarmKit's countdown
 * alarms replace this in Phase 22.
 */
internal object NoTimerSchedulerYet : TimerScheduler {
    override fun canRingOnTime(): Boolean = false

    override fun schedule(timer: Timer) = Unit

    override fun cancel(timerId: Long) = Unit
}

/** iOS shows no timer notifications yet; AlarmKit's own alerts take their place in Phase 22. */
internal object NoTimerNotifierYet : TimerNotifier {
    override fun show(timer: Timer) = Unit

    override fun showWaiting(timer: Timer) = Unit

    override fun showMissed(timer: Timer, reason: MissedReason) = Unit

    override fun hide(timerId: Long) = Unit
}
