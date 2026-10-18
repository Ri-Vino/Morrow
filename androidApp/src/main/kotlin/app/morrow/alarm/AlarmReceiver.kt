package app.morrow.alarm

import app.morrow.morrow
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by AlarmManager when an alarm is due or a snooze is up, and by a snoozed alarm's Dismiss button. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM)
        if (alarmId == NO_ALARM) return
        when (intent.action) {
            ACTION_RING -> {
                RingService.start(context, alarmId)
                goAsync { context.morrow.coordinator.onRang(alarmId) }
            }
            // The alarm's schedule moved on when it first rang, so a snooze leaves it alone.
            ACTION_SNOOZED -> RingService.start(context, alarmId, snoozed = intent.getIntExtra(EXTRA_SNOOZED, 1))
            ACTION_END_SNOOZE -> {
                AndroidAlarmScheduler(context).cancelSnooze(alarmId)
                context.getSystemService(NotificationManager::class.java).cancel(RingNotifications.snoozedId(alarmId))
                // Called off, so it's done with: a temporary alarm deletes itself.
                goAsync { context.morrow.coordinator.onFinished(alarmId) }
            }
        }
    }

    companion object {
        private const val ACTION_RING = "app.morrow.action.RING"
        private const val ACTION_SNOOZED = "app.morrow.action.RING_AFTER_SNOOZE"
        private const val ACTION_END_SNOOZE = "app.morrow.action.END_SNOOZE"
        private const val EXTRA_SNOOZED = "snoozed"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_ALARM_ID, alarmId)

        /** Rings [alarmId] again, snoozed [count] times so far. */
        fun snoozedIntent(context: Context, alarmId: Long, count: Int): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_SNOOZED)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SNOOZED, count)

        /** Ends [alarmId]'s snooze without ringing again. */
        fun endSnoozeIntent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_END_SNOOZE)
                .putExtra(EXTRA_ALARM_ID, alarmId)
    }
}
