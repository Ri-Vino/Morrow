package app.morrow.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.morrow.data.AlarmSetup
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.morrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs only while an alarm rings: sound on the alarm stream with a slow volume ramp, vibration and a wake lock.
 * It always stops itself after [RING_LIMIT].
 */
class RingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val sound by lazy { AlarmTone(this) }
    private var wakeLock: PowerManager.WakeLock? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            else -> ring(intent?.getLongExtra(EXTRA_ALARM_ID, NO_ALARM) ?: NO_ALARM)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        super.onDestroy()
    }

    private fun ring(alarmId: Long) {
        val placeholder = placeholder(alarmId)
        startInForeground(placeholder)
        Ringing.set(placeholder)

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Morrow:ring")
                .apply {
                    setReferenceCounted(false)
                    acquire((RING_LIMIT + 1.minutes).inWholeMilliseconds)
                }
            startVibration()
            handler.postDelayed(::stopRinging, RING_LIMIT.inWholeMilliseconds)
            scope.launch {
                // If the settings can't be read quickly, ring with the defaults rather than not at all.
                val setup = runCatching { withTimeoutOrNull(1.seconds) { morrow.settings.alarmSetup.first() } }.getOrNull()
                    ?: AlarmSetup.Default
                if (wakeLock != null) sound.play(setup.sound, setup.ramp.duration)
            }
        }

        scope.launch {
            // Yesterday's "one small thing" lasts until the next alarm, which is this one.
            morrow.settings.clearIntention()
            val alarm = morrow.repository.get(alarmId) ?: return@launch
            Ringing.set(alarm)
            getSystemService(NotificationManager::class.java).notify(RingNotifications.ID, RingNotifications.build(this@RingService, alarm))
        }
    }

    private fun startInForeground(alarm: Alarm) {
        val notification = RingNotifications.build(this, alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(RingNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(RingNotifications.ID, notification)
        }
    }

    private fun startVibration() {
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, AlarmTone.AlarmAudio)
        }
    }

    private fun stopRinging() {
        release()
        Ringing.set(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        try {
            handler.removeCallbacksAndMessages(null)
            sound.stop()
            vibrator.cancel()
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    /** Shown until the alarm's details load, or if the alarm was deleted as it went off. */
    private fun placeholder(alarmId: Long): Alarm {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return Alarm(alarmId, now.hour, now.minute, "Alarm", emptySet(), DefaultGames, enabled = true)
    }

    companion object {
        private const val ACTION_STOP = "app.morrow.action.STOP_RINGING"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L
        val RING_LIMIT = 10.minutes

        fun start(context: Context, alarmId: Long) {
            context.startForegroundService(Intent(context, RingService::class.java).putExtra(EXTRA_ALARM_ID, alarmId))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RingService::class.java).setAction(ACTION_STOP))
        }
    }
}
