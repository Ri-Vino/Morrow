package app.morrow.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import app.morrow.morrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    private val alarmAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var rampStartedAt = 0L

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
    }

    private val ramp = object : Runnable {
        override fun run() {
            val progress = ((System.currentTimeMillis() - rampStartedAt) / RAMP.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
            // Quadratic, because loudness is perceived roughly logarithmically.
            val volume = MIN_VOLUME + (1f - MIN_VOLUME) * progress * progress
            player?.setVolume(volume, volume)
            if (progress < 1f) handler.postDelayed(this, 1.seconds.inWholeMilliseconds)
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
        val placeholder = RingingAlarm(alarmId, currentTime(), "Alarm")
        startInForeground(placeholder)
        Ringing.set(placeholder)

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Morrow:ring")
                .apply {
                    setReferenceCounted(false)
                    acquire((RING_LIMIT + 1.minutes).inWholeMilliseconds)
                }
            startSound()
            startVibration()
            handler.postDelayed(::stopRinging, RING_LIMIT.inWholeMilliseconds)
        }

        scope.launch {
            val alarm = morrow.repository.get(alarmId) ?: return@launch
            val details = RingingAlarm(alarm.id, "%02d:%02d".format(alarm.hour, alarm.minute), alarm.label)
            Ringing.set(details)
            getSystemService(NotificationManager::class.java).notify(RingNotifications.ID, RingNotifications.build(this@RingService, details))
        }
    }

    private fun startInForeground(alarm: RingingAlarm) {
        val notification = RingNotifications.build(this, alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(RingNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(RingNotifications.ID, notification)
        }
    }

    private fun startSound() {
        for (uri in soundCandidates()) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAudio)
                    setDataSource(this@RingService, uri)
                    isLooping = true
                    prepare()
                    setVolume(MIN_VOLUME, MIN_VOLUME)
                    start()
                }
            }
            started.onSuccess {
                player = it
                rampStartedAt = System.currentTimeMillis()
                handler.post(ramp)
                return
            }
            Log.w(TAG, "Couldn't play $uri", started.exceptionOrNull())
        }
        // Last resort, so an alarm is never silent.
        tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).apply {
            startTone(ToneGenerator.TONE_SUP_RINGTONE)
        }
    }

    private fun soundCandidates(): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    ).distinct()

    private fun startVibration() {
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, alarmAudio)
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
            player?.let { runCatching { it.stop() }; it.release() }
            tone?.release()
            vibrator.cancel()
        } finally {
            player = null
            tone = null
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    private fun currentTime(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "%02d:%02d".format(now.hour, now.minute)
    }

    companion object {
        private const val TAG = "RingService"
        private const val ACTION_STOP = "app.morrow.action.STOP_RINGING"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L
        private const val MIN_VOLUME = 0.05f
        private val RAMP = 60.seconds
        val RING_LIMIT = 10.minutes

        fun start(context: Context, alarmId: Long) {
            context.startForegroundService(Intent(context, RingService::class.java).putExtra(EXTRA_ALARM_ID, alarmId))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RingService::class.java).setAction(ACTION_STOP))
        }
    }
}
