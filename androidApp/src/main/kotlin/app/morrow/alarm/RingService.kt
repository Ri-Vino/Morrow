package app.morrow.alarm

import android.app.Notification
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
import android.util.Log
import app.morrow.data.AlarmSetup
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.morrow
import app.morrow.timers.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * Runs only while something rings: sound on the alarm stream, vibration and a wake lock. It always stops itself after
 * [RING_LIMIT].
 *
 * It rings in one of two modes, and an alarm always comes first:
 * - **Alarm**, from AlarmReceiver: the alarm's sound, ramping up, until its games are done. Timers that end meanwhile
 *   don't ring but wait in `TimerCoordinator.waiting`, until the alarm's Morning screen has gone (RingActivity lets them
 *   go then) or, if the alarm reaches its safety stop and no Morning screen follows, until that.
 * - **Timers**, from TimerReceiver: the Bell for every timer that has ended, until `TimerCoordinator.ringing` empties
 *   (Stop, "+1 minute", or the timers' safety stop). An alarm going off meanwhile takes over, and the timers wait
 *   behind it as above.
 *
 * Each mode has its own safety stop, counted from when it started ringing: for an alarm from when it went off, for
 * timers from when the latest of them ended. Leaving a mode cancels its safety stop, so an alarm that takes over from
 * timers gets its full ten minutes, and so do the timers when they ring again after it.
 */
class RingService : Service() {

    private enum class Mode { IDLE, ALARM, TIMERS }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val sound by lazy { AlarmTone(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var mode = Mode.IDLE
    private var lastStartId = 0
    private var followingTimers: Job? = null
    private val alarmSafetyStop = Runnable { stopAlarm(gamesDone = false) }
    private val timerSafetyStop = Runnable { timeOutTimers() }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> stopAlarm(gamesDone = true)
            ACTION_RING_TIMERS -> ringTimers()
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
        startInForeground(RingNotifications.ID, RingNotifications.build(this, placeholder))
        Ringing.set(placeholder)

        if (mode == Mode.TIMERS) {
            // The alarm takes over. The timers wait for its Morning screen, then ring again.
            leaveTimers()
            scope.launch { morrow.timers.holdRinging() }
        }
        if (mode != Mode.ALARM) {
            mode = Mode.ALARM
            holdWakeLock()
            startVibration()
            handler.postDelayed(alarmSafetyStop, RING_LIMIT.inWholeMilliseconds)
            scope.launch {
                // If the settings can't be read quickly, ring with the defaults rather than not at all.
                val setup = runCatching { withTimeoutOrNull(1.seconds) { morrow.settings.alarmSetup.first() } }.getOrNull()
                    ?: AlarmSetup.Default
                if (mode == Mode.ALARM) sound.play(setup.sound, setup.ramp.duration)
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

    /** Games done, or the alarm's safety stop. */
    private fun stopAlarm(gamesDone: Boolean) {
        if (mode != Mode.ALARM) return stopIfIdle()
        handler.removeCallbacks(alarmSafetyStop)
        quiet()
        mode = Mode.IDLE
        Ringing.set(null)
        // After the games comes the Morning screen, and RingActivity lets waiting timers go once it has gone. After the
        // safety stop nothing follows, so they ring now.
        if (!gamesDone) morrow.timers.releaseWaiting()
        stopIfIdle()
    }

    /** A timer has ended, or several: ring every timer that has, unless an alarm is ringing or its Morning screen shows. */
    private fun ringTimers() {
        val behindAlarm = mode == Mode.ALARM || Ringing.morningShowing
        // startForegroundService() needs a notification straight away, whatever happens next.
        when {
            mode == Mode.ALARM -> startInForeground(RingNotifications.ID, RingNotifications.build(this, Ringing.current.value ?: placeholder(NO_ALARM)))
            behindAlarm -> startInForeground(TimerNotifications.RINGING_ID, TimerNotifications.holding(this))
            else -> {
                startInForeground(TimerNotifications.RINGING_ID, TimerNotifications.ringing(this, morrow.timers.ringing.value))
                if (mode != Mode.TIMERS) {
                    mode = Mode.TIMERS
                    startVibration()
                    sound.playBell()
                }
                // Each timer that ends gets its own ten minutes, so the safety stop starts again.
                holdWakeLock()
                handler.removeCallbacks(timerSafetyStop)
                handler.postDelayed(timerSafetyStop, RING_LIMIT.inWholeMilliseconds)
            }
        }

        scope.launch {
            val ringing = runCatching { morrow.timers.ringEnded(behindAlarm) }
                .onFailure { Log.e(TAG, "Couldn't read the timers", it) }
                .getOrDefault(emptyList())
            when {
                behindAlarm -> stopIfIdle()
                // An alarm took over meanwhile; the timers are waiting behind it.
                mode != Mode.TIMERS -> Unit
                // Stopped from its notification just as it ended, say.
                ringing.isEmpty() -> stopTimers()
                followingTimers == null -> followingTimers = scope.launch {
                    morrow.timers.ringing.collect { timers -> if (timers.isEmpty()) stopTimers() else showRinging(timers) }
                }
            }
        }
    }

    private fun showRinging(timers: List<Timer>) {
        getSystemService(NotificationManager::class.java).notify(TimerNotifications.RINGING_ID, TimerNotifications.ringing(this, timers))
    }

    private fun stopTimers() {
        if (mode != Mode.TIMERS) return
        leaveTimers()
        mode = Mode.IDLE
        stopIfIdle()
    }

    /** The timers' safety stop: quiet at once, then each unanswered timer leaves a notification before the service goes. */
    private fun timeOutTimers() {
        if (mode != Mode.TIMERS) return
        leaveTimers()
        mode = Mode.IDLE
        scope.launch {
            withTimeoutOrNull(5.seconds) {
                runCatching { morrow.timers.timeOutRinging() }.onFailure { Log.e(TAG, "Couldn't time out the timers", it) }
            }
            stopIfIdle()
        }
    }

    /** Stops the Bell, its safety stop and its notification. Which timers ring is the coordinator's business. */
    private fun leaveTimers() {
        handler.removeCallbacks(timerSafetyStop)
        followingTimers?.cancel()
        followingTimers = null
        quiet()
        getSystemService(NotificationManager::class.java).cancel(TimerNotifications.RINGING_ID)
    }

    /** Stops the service once nothing rings. A start that came in meanwhile keeps it going, as it's newer than [lastStartId]. */
    private fun stopIfIdle() {
        if (mode != Mode.IDLE) return
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun startInForeground(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(id, notification)
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

    /** Held while anything rings. Each new ring renews its timeout, which always outlasts the safety stop. */
    private fun holdWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Morrow:ring")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire((RING_LIMIT + 1.minutes).inWholeMilliseconds)
    }

    private fun quiet() {
        sound.stop()
        vibrator.cancel()
    }

    private fun release() {
        try {
            handler.removeCallbacksAndMessages(null)
            quiet()
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
        private const val TAG = "RingService"
        private const val ACTION_STOP = "app.morrow.action.STOP_RINGING"
        private const val ACTION_RING_TIMERS = "app.morrow.action.RING_TIMERS"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L
        val RING_LIMIT = 10.minutes

        fun start(context: Context, alarmId: Long) {
            context.startForegroundService(Intent(context, RingService::class.java).putExtra(EXTRA_ALARM_ID, alarmId))
        }

        /** Rings every timer that has ended. From TimerReceiver, when a timer's exact alarm goes off. */
        fun ringTimers(context: Context) {
            context.startForegroundService(Intent(context, RingService::class.java).setAction(ACTION_RING_TIMERS))
        }

        /** The alarm's games are done. */
        fun stop(context: Context) {
            context.startService(Intent(context, RingService::class.java).setAction(ACTION_STOP))
        }
    }
}
