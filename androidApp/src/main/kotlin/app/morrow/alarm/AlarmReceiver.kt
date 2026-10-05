package app.morrow.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.morrow.morrow

/** Fired by AlarmManager when an alarm is due. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RING) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM)
        if (alarmId == NO_ALARM) return

        RingService.start(context, alarmId)
        goAsync { context.morrow.coordinator.onRang(alarmId) }
    }

    companion object {
        private const val ACTION_RING = "app.morrow.action.RING"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_ALARM_ID, alarmId)
    }
}
