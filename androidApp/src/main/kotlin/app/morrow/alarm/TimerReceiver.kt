package app.morrow.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.morrow.morrow

/** Fired by AlarmManager when a timer ends, and by the buttons on the timers' notifications. */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timers = context.morrow.timers
        val id = intent.data?.schemeSpecificPart?.toLongOrNull()
        when (intent.action) {
            // RingService looks for every timer that has ended, so timers ending together ring together.
            ACTION_ENDED -> RingService.ringTimers(context)
            ACTION_STOP_RINGING -> {
                context.morrow.timerVoice.hush()
                goAsync { timers.stopRinging() }
            }
            ACTION_SNOOZE_RINGING -> {
                context.morrow.timerVoice.hush()
                goAsync { timers.snoozeRinging() }
            }
            ACTION_PAUSE -> if (id != null) goAsync { timers.pause(id) }
            ACTION_RESUME -> if (id != null) goAsync { timers.resume(id) }
            ACTION_ADD_MINUTE -> if (id != null) goAsync { timers.addMinute(id) }
            ACTION_STOP -> if (id != null) goAsync { timers.stop(id) }
        }
    }

    companion object {
        private const val ACTION_ENDED = "app.morrow.action.TIMER_ENDED"
        const val ACTION_PAUSE = "app.morrow.action.PAUSE_TIMER"
        const val ACTION_RESUME = "app.morrow.action.RESUME_TIMER"
        const val ACTION_ADD_MINUTE = "app.morrow.action.ADD_MINUTE"
        const val ACTION_STOP = "app.morrow.action.STOP_TIMER"
        const val ACTION_STOP_RINGING = "app.morrow.action.STOP_RINGING_TIMERS"
        const val ACTION_SNOOZE_RINGING = "app.morrow.action.SNOOZE_RINGING_TIMERS"

        /** For AlarmManager. Each timer's has its own data, so each is its own PendingIntent. */
        fun endedIntent(context: Context, timerId: Long): Intent = intent(context, ACTION_ENDED, timerId)

        /** A notification button doing [action] to [timerId], or to every ringing timer when it's null. */
        fun action(context: Context, action: String, timerId: Long? = null): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                intent(context, action, timerId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun intent(context: Context, action: String, timerId: Long?): Intent =
            Intent(context, TimerReceiver::class.java)
                .setAction(action)
                .apply { if (timerId != null) data = Uri.fromParts(SCHEME, timerId.toString(), null) }

        private const val SCHEME = "timer"
    }
}
