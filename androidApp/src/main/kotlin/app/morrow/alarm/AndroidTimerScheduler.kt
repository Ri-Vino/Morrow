package app.morrow.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import app.morrow.platform.TimerScheduler
import app.morrow.timers.Timer

/**
 * Timers ring through exact alarms that wake the phone even in Doze, but unlike `setAlarmClock()` they don't show as
 * the next alarm on the lock screen. Nothing runs while a timer counts down.
 *
 * They're set on the wall clock (RTC_WAKEUP), so they come back after a reboot at the same moment; changing the clock
 * by hand moves them with it, which is accepted.
 */
class AndroidTimerScheduler(private val context: Context) : TimerScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canRingOnTime(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(timer: Timer) {
        val endsAt = timer.endsAt?.toEpochMilliseconds() ?: return cancel(timer.id)
        val ring = ringIntent(timer.id, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Only a timer already running gets here, as none can start without the permission. Late beats never.
            Log.w(TAG, "Exact alarms not allowed; timer ${timer.id} may ring late")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, ring)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, ring)
    }

    override fun cancel(timerId: Long) {
        ringIntent(timerId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun ringIntent(timerId: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, TimerReceiver.endedIntent(context, timerId), PendingIntent.FLAG_IMMUTABLE or flags)

    private companion object {
        const val TAG = "TimerScheduler"
    }
}
