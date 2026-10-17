package app.morrow.alarm

import app.morrow.R
import app.morrow.domain.Alarm
import app.morrow.domain.occurrenceNear
import app.morrow.ui.ring.snoozeLabel
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal object RingNotifications {
    const val ID = 1
    private const val CHANNEL = "ringing"
    private const val SNOOZED_CHANNEL = "snoozed"
    private const val SNOOZED_ID_BASE = 1000

    /** The notification for [alarmId]'s snooze, one for each alarm. */
    fun snoozedId(alarmId: Long): Int = SNOOZED_ID_BASE + alarmId.toInt()

    fun build(context: Context, alarm: Alarm): Notification {
        ensureChannel(context)
        val ringScreen = PendingIntent.getActivity(
            context,
            0,
            ringScreenIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = snoozeLabel(alarm, Ringing.snoozed)?.let { label ->
            val intent = PendingIntent.getService(context, 0, RingService.snoozeIntent(context), PendingIntent.FLAG_IMMUTABLE)
            Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_alarm), label, intent).build()
        }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(ringingTime(alarm))
            .setContentText(alarm.label)
            .apply { if (snooze != null) addAction(snooze) }
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(ringScreen)
            .setFullScreenIntent(ringScreen, true)
            .build()
    }

    /** While [alarm] is snoozed: when it'll ring again, and Dismiss, which calls the snooze off. */
    fun snoozed(context: Context, alarm: Alarm, until: Instant): Notification {
        val channel = NotificationChannel(SNOOZED_CHANNEL, "Snoozed alarms", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while an alarm is snoozed, until it rings again"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val dismiss = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            AlarmReceiver.endSnoozeIntent(context, alarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = until.toLocalDateTime(TimeZone.currentSystemDefault())
        return Notification.Builder(context, SNOOZED_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Snoozed until %02d:%02d".format(at.hour, at.minute))
            .setContentText(alarm.label)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_alarm), "Dismiss", dismiss).build())
            .build()
    }

    fun ringScreenIntent(context: Context): Intent =
        Intent(context, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    /** The phone's time the alarm rang at, which for an alarm on another city's clock isn't the time it was set for. */
    private fun ringingTime(alarm: Alarm): String {
        val zone = TimeZone.currentSystemDefault()
        val at = alarm.occurrenceNear(Clock.System.now(), zone).toLocalDateTime(zone)
        return "%02d:%02d".format(at.hour, at.minute)
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
