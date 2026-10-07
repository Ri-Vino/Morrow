package app.morrow.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.morrow.R
import app.morrow.domain.Alarm

internal object RingNotifications {
    const val ID = 1
    private const val CHANNEL = "ringing"

    fun build(context: Context, alarm: Alarm): Notification {
        ensureChannel(context)
        val ringScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("%02d:%02d".format(alarm.hour, alarm.minute))
            .setContentText(alarm.label)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(ringScreen)
            .setFullScreenIntent(ringScreen, true)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while an alarm rings"
            // RingService plays the sound and vibration itself, on the alarm stream.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
