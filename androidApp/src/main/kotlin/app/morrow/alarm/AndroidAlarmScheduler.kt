package app.morrow.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.morrow.MainActivity
import app.morrow.domain.Alarm
import app.morrow.platform.AlarmScheduler
import kotlin.time.Instant

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(alarm: Alarm, at: Instant) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // The app warns about this; BootReceiver reschedules once the permission comes back.
            Log.w(TAG, "Exact alarms not allowed; alarm ${alarm.id} not scheduled")
            return
        }
        // Tapping the alarm icon in the status bar opens the app.
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val info = AlarmManager.AlarmClockInfo(at.toEpochMilliseconds(), show)
        alarmManager.setAlarmClock(info, ringIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    override fun cancel(alarmId: Long) {
        ringIntent(alarmId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun ringIntent(alarmId: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            AlarmReceiver.intent(context, alarmId),
            PendingIntent.FLAG_IMMUTABLE or flags,
        )

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
