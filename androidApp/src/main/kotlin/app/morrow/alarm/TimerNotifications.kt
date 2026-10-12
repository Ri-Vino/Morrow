package app.morrow.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import app.morrow.MainActivity
import app.morrow.R
import app.morrow.platform.MissedReason
import app.morrow.platform.TimerNotifier
import app.morrow.timers.Timer
import app.morrow.timers.clockText
import app.morrow.timers.name
import app.morrow.timers.totalText
import app.morrow.ui.world.cityClock
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * One notification per timer: ongoing and quiet while it runs, with a countdown and Pause, +1 min and Stop. Its buttons
 * go to [TimerReceiver]; nothing runs in the background. [ringing] is the one RingService shows while timers ring.
 */
class TimerNotifications(private val context: Context) : TimerNotifier {

    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun show(timer: Timer) = show(timer, cantResume = false)

    /** Without exact alarms a timer can't resume, so tapping this opens the screen to allow them. */
    override fun showCantResume(timer: Timer) = show(timer, cantResume = true)

    private fun show(timer: Timer, cantResume: Boolean) {
        val endsAt = timer.endsAt
        val notification = builder(context, CHANNEL_RUNNING, timer.name)
            .setOngoing(true)
            .setContentIntent(if (cantResume && endsAt == null) allowExactAlarms(context) else openApp(context))
            .apply {
                if (endsAt != null) {
                    setContentText("${totalText(timer.duration)} · ends at ${clock(endsAt)}")
                    setWhen(endsAt.toEpochMilliseconds())
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    addAction(button("Pause", TimerReceiver.ACTION_PAUSE, timer.id))
                } else {
                    val left = "Paused · ${clockText(timer.remaining ?: timer.duration)} left"
                    setContentText(if (cantResume) "$left · Allow Alarms & reminders to resume" else left)
                    setShowWhen(false)
                    addAction(button("Resume", TimerReceiver.ACTION_RESUME, timer.id))
                }
            }
            .addAction(button("+1 min", TimerReceiver.ACTION_ADD_MINUTE, timer.id))
            .addAction(button("Stop", TimerReceiver.ACTION_STOP, timer.id))
            .build()
        manager.notify(notificationId(timer.id), notification)
    }

    override fun showWaiting(timer: Timer) {
        val ended = timer.endsAt?.let { "Ended at ${clock(it)} · " }.orEmpty()
        val notification = builder(context, CHANNEL_RUNNING, timer.name)
            .setContentText("${ended}rings after your alarm")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .addAction(button("Stop", TimerReceiver.ACTION_STOP, timer.id))
            .build()
        manager.notify(notificationId(timer.id), notification)
    }

    override fun showMissed(timer: Timer, reason: MissedReason) {
        val ended = timer.endsAt?.let { "Ended at ${clock(it)}" } ?: "Ended"
        val text = when (reason) {
            MissedReason.PHONE_OFF -> "$ended while your phone was off"
            MissedReason.TOO_LATE -> "$ended, when Morrow couldn’t ring it"
            MissedReason.NOT_STOPPED -> "$ended and rang for 10 minutes"
        }
        val notification = builder(context, CHANNEL_MISSED, timer.name)
            .setContentText(text)
            .setAutoCancel(true)
            .setShowWhen(true)
            .apply { timer.endsAt?.let { setWhen(it.toEpochMilliseconds()) } }
            .setContentIntent(openApp(context))
            .build()
        manager.notify(notificationId(timer.id), notification)
    }

    override fun hide(timerId: Long) = manager.cancel(notificationId(timerId))

    companion object {
        /** RingService's notification while timers ring. Alarms use [RingNotifications.ID]. */
        const val RINGING_ID = 2

        private const val CHANNEL_RUNNING = "timers"
        private const val CHANNEL_RINGING = "timer_done"
        private const val CHANNEL_MISSED = "timer_missed"
        private const val CHANNEL_ALERT = "timer_alert"

        /** [ringWithoutService]'s alert. */
        private const val ALERT_ID = 3

        /**
         * While [timers] ring: TimerRingActivity over the lock screen when the phone is locked or off, otherwise a
         * heads-up with Stop and +1 minute. Empty while RingService is still finding which timers have ended.
         */
        fun ringing(context: Context, timers: List<Timer>): Notification {
            val doneScreen = PendingIntent.getActivity(
                context,
                0,
                Intent(context, TimerRingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val only = timers.singleOrNull()
            val title = when {
                timers.isEmpty() -> "Timer done"
                only != null -> only.name
                else -> "${timers.size} timers done"
            }
            val text = when {
                only != null -> "Timer done" + only.endsAt?.let { " · ended at ${clock(it)}" }.orEmpty()
                else -> timers.joinToString(" · ") { it.name }
            }
            return builder(context, CHANNEL_RINGING, title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                // A timer joining those already ringing updates the text without starting over.
                .setOnlyAlertOnce(true)
                .setContentIntent(doneScreen)
                .setFullScreenIntent(doneScreen, true)
                .addAction(button(context, "Stop", TimerReceiver.ACTION_STOP_RINGING))
                .addAction(button(context, "+1 minute", TimerReceiver.ACTION_SNOOZE_RINGING))
                .build()
        }

        /**
         * For when a timer ends but RingService can't start, as for a late alarm without the exact-alarm permission on
         * Android 12: an alert that plays the Bell itself, and opens the Timer done screen over the lock screen, which
         * starts RingService from the front.
         */
        fun ringWithoutService(context: Context) {
            val doneScreen = PendingIntent.getActivity(
                context,
                1,
                Intent(context, TimerRingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(TimerRingActivity.EXTRA_START_RINGING, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = builder(context, CHANNEL_ALERT, "Timer done")
                .setContentText("Tap to see your timer")
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(doneScreen)
                .setFullScreenIntent(doneScreen, true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
        }

        /** Takes down [ringWithoutService]'s alert, once RingService rings. */
        fun hideAlert(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)

        /** For the moment it takes RingService to put a timer that ended behind an alarm's Morning screen on hold. */
        fun holding(context: Context): Notification =
            builder(context, CHANNEL_RUNNING, "Timer done")
                .setContentText("Rings after your alarm")
                .setShowWhen(false)
                .build()

        private fun builder(context: Context, channel: String, title: String): Notification.Builder {
            ensureChannels(context)
            return Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(title)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
        }

        private fun button(context: Context, title: String, action: String, timerId: Long? = null): Notification.Action =
            Notification.Action.Builder(null as Icon?, title, TimerReceiver.action(context, action, timerId)).build()

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun allowExactAlarms(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun clock(at: Instant) = cityClock(at, TimeZone.currentSystemDefault())

        /** Clear of the alarm's and RINGING_ID; timer ids are never reused, so neither are these. */
        private fun notificationId(timerId: Long) = 1_000 + timerId.toInt()

        private fun ensureChannels(context: Context) {
            val running = NotificationChannel(CHANNEL_RUNNING, "Timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Each running timer, counting down"
            }
            val ringing = NotificationChannel(CHANNEL_RINGING, "Timer done", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while a timer rings"
                // RingService plays the Bell and vibrates itself, on the alarm stream.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val missed = NotificationChannel(CHANNEL_MISSED, "Missed timers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Timers that ended while your phone was off, or that no one stopped"
            }
            val alert = NotificationChannel(CHANNEL_ALERT, "Timer done without alarms & reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A timer that ended while Morrow couldn't ring it as usual"
                setSound(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.bell}"),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(running, ringing, missed, alert))
        }
    }

    private fun button(title: String, action: String, timerId: Long) = button(context, title, action, timerId)
}
