package app.morrow.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.morrow.morrow

/**
 * Android forgets scheduled alarms and timers on reboot and can misplace them on clock changes, so put them all back.
 * A timer that ended while the phone was off rings if it ended within the last 10 minutes, and otherwise leaves a
 * notification saying so.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val app = context.morrow
        goAsync {
            // Each on its own, so a problem with one doesn't keep the other from coming back.
            runCatching { app.coordinator.rescheduleAll() }.onFailure { Log.e(TAG, "Couldn't restore the alarms", it) }
            app.timers.rescheduleAll(afterReboot = intent.action in BOOTS)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val BOOTS = setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED)

        @SuppressLint("InlinedApi") // Older versions never send the newer actions.
        val HANDLED = BOOTS + setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
