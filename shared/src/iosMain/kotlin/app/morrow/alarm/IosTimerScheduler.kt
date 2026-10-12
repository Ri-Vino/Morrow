package app.morrow.alarm

import app.morrow.platform.MissedReason
import app.morrow.platform.TimerNotifier
import app.morrow.platform.TimerScheduler
import app.morrow.timers.Timer
import app.morrow.timers.timerAlarmKitId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Hands timers to AlarmKit as countdowns, which ring like the Clock app's timers, even with Morrow closed. Each runs
 * under its own id in AlarmKit, from [timerAlarmKitId], with its note as the alert's title.
 */
class IosTimerScheduler(private val alarmKit: AlarmKitBridge, private val clock: Clock = Clock.System) : TimerScheduler {

    override fun canRingOnTime(): Boolean = alarmKit.authorized

    override fun schedule(timer: Timer) {
        val endsAt = timer.endsAt ?: return
        // A fresh countdown for what's left is always right, even after a minute was added while paused, so resuming
        // hands it over again rather than resuming AlarmKit's.
        alarmKit.startCountdown(timerAlarmKitId(timer.id), timer.id, timer.alertTitle, seconds(endsAt - clock.now()), BELL)
    }

    override fun pause(timer: Timer) {
        val left = timer.remaining ?: return
        alarmKit.pauseCountdown(timerAlarmKitId(timer.id), timer.id, timer.alertTitle, seconds(left), BELL)
    }

    override fun cancel(timerId: Long) = alarmKit.cancel(timerAlarmKitId(timerId))

    /** AlarmKit counts down at least a second; an end already past rings after that one. */
    private fun seconds(left: Duration): Double = left.coerceAtLeast(1.seconds).toDouble(DurationUnit.SECONDS)

    private val Timer.alertTitle: String get() = note.ifBlank { "Timer" }

    private companion object {
        /** The Bell, made by tools/make_sounds.py. */
        const val BELL = "bell.caf"
    }
}

/**
 * Nothing to show on iOS: AlarmKit's own countdown, on the Lock Screen and in the Dynamic Island, stands in for the
 * running and paused notifications. It rings timers itself, through alarms and reboots, so none waits behind an alarm
 * or is missed, and Morrow is only told of one when it's opened from its alert.
 */
object IosTimerNotifier : TimerNotifier {
    override fun show(timer: Timer) = Unit

    override fun showWaiting(timer: Timer) = Unit

    override fun showMissed(timer: Timer, reason: MissedReason) = Unit

    override fun hide(timerId: Long) = Unit
}
