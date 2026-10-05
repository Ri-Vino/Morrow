package app.morrow.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.morrow.morrow

/** Android forgets scheduled alarms on reboot and can misplace them on clock changes, so put them all back. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        goAsync { context.morrow.coordinator.rescheduleAll() }
    }

    private companion object {
        @SuppressLint("InlinedApi") // Older versions never send the newer actions.
        val HANDLED = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
