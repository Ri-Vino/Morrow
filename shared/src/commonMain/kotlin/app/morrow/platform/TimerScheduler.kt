package app.morrow.platform

import app.morrow.timers.Timer

/** Hands running timers to the OS, which wakes the app when one ends. Nothing in the app stays running meanwhile. */
interface TimerScheduler {
    /** False when the OS wouldn't ring a timer on time, as without Android's exact-alarm permission. Nothing is started then. */
    fun canRingOnTime(): Boolean

    /**
     * Rings [timer] at its [Timer.endsAt], replacing any time set for it before. An end already past rings straight
     * away. Does nothing for a paused timer.
     */
    fun schedule(timer: Timer)

    fun cancel(timerId: Long)
}
