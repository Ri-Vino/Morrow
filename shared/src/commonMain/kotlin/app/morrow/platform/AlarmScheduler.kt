package app.morrow.platform

import app.morrow.domain.Alarm
import kotlin.time.Instant

/** Hands alarms to the OS, which wakes the app when they're due. Nothing in the app stays running. */
interface AlarmScheduler {
    fun schedule(alarm: Alarm, at: Instant)
    fun cancel(alarmId: Long)
}
