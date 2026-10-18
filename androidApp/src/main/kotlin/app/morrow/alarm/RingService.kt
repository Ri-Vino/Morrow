package app.morrow.alarm

import app.morrow.data.AlarmSetup
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.RingSound
import app.morrow.domain.canSnooze
import app.morrow.morrow
import app.morrow.platform.play
import app.morrow.timers.Timer
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Runs only while something rings: sound on the alarm stream, vibration and a wake lock. It always stops itself after
 * [RING_LIMIT].
 *
 * It rings in one of two modes, and an alarm always comes first:
 * - **Alarm**, from AlarmReceiver: the alarm's sound, ramping up, until its games are done. Timers that end meanwhile
 *   don't ring but wait in `TimerCoordinator.waiting`, until the alarm's Morning screen has gone or has shown for ten
 *   minutes (RingActivity lets them go then) or, if the alarm reaches its safety stop and no Morning screen follows,
 *   until that.
 * - **Timers**, from TimerReceiver: the Bell for every timer that has ended, with Pip reading their notes over it
 *   (`TimerVoice`), until `TimerCoordinator.ringing` empties (Stop, "+1 minute", or the timers' safety stop). An alarm
 *   going off meanwhile takes over, silencing Pip too, and the timers wait behind it as above.
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
    private var findingTimers = 0
    private var followingTimers: Job? = null
    private var readingNotes: Job? = null
    private var overlay: View? = null
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val alarmSafetyStop = Runnable { stopAlarm(gamesDone = false) }
    private val timerSafetyStop = Runnable { timeOutTimers() }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The games turn the alarm down, or mute it for a while, and the vibration down to a light tap; the Bell for
        // timers always rings out.
        scope.launch {
            Ringing.gameLevel.collect { level ->
                if (mode != Mode.ALARM) return@collect
                sound.setLevel(level)
                startVibration(gentle = level < 1f)
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> stopAlarm(gamesDone = true)
            ACTION_SNOOZE -> snooze()
            ACTION_RING_TIMERS -> ringTimers()
            else -> ring(intent?.getLongExtra(EXTRA_ALARM_ID, NO_ALARM) ?: NO_ALARM, intent?.getIntExtra(EXTRA_SNOOZED, 0) ?: 0)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        super.onDestroy()
    }

    private fun ring(alarmId: Long, snoozed: Int) {
        val placeholder = placeholder(alarmId)
        Ringing.snoozed = snoozed
        getSystemService(NotificationManager::class.java).cancel(RingNotifications.snoozedId(alarmId))
        startInForeground(RingNotifications.ID, RingNotifications.build(this, placeholder))
        Ringing.set(placeholder)
        showRingScreen()

        if (mode == Mode.TIMERS) {
            // The alarm takes over. The timers wait for its Morning screen, then ring again.
            leaveTimers()
            scope.launch { morrow.timers.holdRinging() }
        }
        if (mode != Mode.ALARM) {
            mode = Mode.ALARM
            Ringing.setGameLevel(1f)
            holdWakeLock()
            startVibration()
            handler.postDelayed(alarmSafetyStop, RING_LIMIT.inWholeMilliseconds)
            scope.launch {
                // If the settings or the alarm can't be read quickly, ring with the defaults rather than not at all.
                val setup = runCatching { withTimeoutOrNull(1.seconds) { morrow.settings.alarmSetup.first() } }.getOrNull()
                    ?: AlarmSetup.Default
                val own = runCatching { withTimeoutOrNull(1.seconds) { morrow.repository.get(alarmId)?.sound } }.getOrNull()
                val file = (own as? RingSound.Own)?.let { mine ->
                    runCatching { withTimeoutOrNull(1.seconds) { morrow.ownSounds.get(mine.id) } }.getOrNull()
                }
                if (mode == Mode.ALARM) sound.play(own, setup.sound, setup.ramp.duration, listOfNotNull(file))
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

    /**
     * The full-screen intent only fills the screen while the phone is locked or asleep; in use, Android shrinks it to a
     * banner. With "Display over other apps" allowed, an invisible overlay lets the ring screen open over whatever is
     * in use, as Android only lets an app with a visible window start a screen from the background.
     */
    private fun showRingScreen() {
        if (!Settings.canDrawOverlays(this)) return
        val window = overlay ?: View(this).also { view ->
            try {
                windowManager.addView(view, OverlayParams)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't show the overlay; the notification's full-screen intent stands in", e)
                return
            }
            overlay = view
        }
        // The overlay must be on screen before the screen opens, and it goes once the ring screen has.
        window.post {
            try {
                startActivity(RingNotifications.ringScreenIntent(this))
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't open the ring screen", e)
            }
            handler.postDelayed(::removeOverlay, OVERLAY_HOLD.inWholeMilliseconds)
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    /**
     * Puts the ringing alarm off for its snooze length: it rings again then, and a notification says when, with a way
     * to call it off. Does nothing if it can't be snoozed again.
     */
    private fun snooze() {
        val alarm = Ringing.current.value
        if (mode != Mode.ALARM || alarm == null || !canSnooze(alarm, Ringing.snoozed)) return
        val until = Clock.System.now() + alarm.snoozeMinutes.minutes
        AndroidAlarmScheduler(this).snooze(alarm.id, until, Ringing.snoozed + 1)
        getSystemService(NotificationManager::class.java)
            .notify(RingNotifications.snoozedId(alarm.id), RingNotifications.snoozed(this, alarm, until))
        stopAlarm(gamesDone = false, snoozed = true)
    }

    /** Games done, the alarm's safety stop, or a snooze, after which it rings again. */
    private fun stopAlarm(gamesDone: Boolean, snoozed: Boolean = false) {
        if (mode != Mode.ALARM) return stopIfIdle()
        handler.removeCallbacks(alarmSafetyStop)
        // Done with, unless it was only snoozed, so a temporary alarm can delete itself. Not cancelled as the service stops.
        val ringing = Ringing.current.value
        if (ringing != null && !snoozed) scope.launch { withContext(NonCancellable) { morrow.coordinator.onFinished(ringing.id) } }
        quiet()
        mode = Mode.IDLE
        Ringing.set(null)
        Ringing.setGameLevel(1f)
        // After the games comes the Morning screen, and RingActivity lets waiting timers go once it has gone. After the
        // safety stop nothing follows, so they ring now.
        if (!gamesDone) morrow.timers.releaseWaiting()
        stopIfIdle()
    }

    /** A timer has ended, or several: ring every timer that has, unless an alarm is ringing or its Morning screen shows. */
    private fun ringTimers() {
        // startForegroundService() needs a notification straight away, whatever happens next.
        when {
            mode == Mode.ALARM -> startInForeground(RingNotifications.ID, RingNotifications.build(this, Ringing.current.value ?: placeholder(NO_ALARM)))
            Ringing.morningShowing -> startInForeground(TimerNotifications.RINGING_ID, TimerNotifications.holding(this))
            else -> ringBell()
        }

        // While any timer is being found, nothing may stop the service: the timer's alarm has gone off, and nothing
        // would set it again.
        findingTimers++
        scope.launch {
            val ringing = try {
                withContext(NonCancellable) {
                    // Asked as the timers join, so an alarm or its Morning screen ending meanwhile can't strand them.
                    morrow.timers.ringEnded { mode == Mode.ALARM || Ringing.morningShowing }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't read the timers", e)
                emptyList()
            } finally {
                findingTimers--
            }
            val next = afterFindingTimers(
                alarm = mode == Mode.ALARM,
                bell = mode == Mode.TIMERS,
                ringing = ringing.isNotEmpty(),
                following = followingTimers != null,
            )
            when (next) {
                AfterFinding.WAIT -> Unit
                AfterFinding.STOP -> if (mode == Mode.TIMERS) stopTimers() else stopIfIdle()
                AfterFinding.RING -> {
                    ringBell()
                    followTimers()
                }
                AfterFinding.FOLLOW -> followTimers()
            }
        }
    }

    /** Rings the Bell for the timers that have ended. Each gets its own ten minutes, so the safety stop starts again. */
    private fun ringBell() {
        startInForeground(TimerNotifications.RINGING_ID, TimerNotifications.ringing(this, morrow.timers.ringing.value))
        if (mode != Mode.TIMERS) {
            mode = Mode.TIMERS
            startVibration()
            sound.playBell()
        }
        holdWakeLock()
        handler.removeCallbacks(timerSafetyStop)
        handler.postDelayed(timerSafetyStop, RING_LIMIT.inWholeMilliseconds)
    }

    /** Has Pip read the notes, and follows which timers ring until none does. */
    private fun followTimers() {
        // Started first, so that if the timers have been stopped meanwhile, stopTimers() below cancels it too.
        readingNotes = scope.launch { readNotes() }
        followingTimers = scope.launch {
            morrow.timers.ringing.collect { timers -> if (timers.isEmpty()) stopTimers() else showRinging(timers) }
        }
    }

    /** Pip reads the ringing timers' notes, ducking the Bell while he speaks, until [leaveTimers] cancels him. */
    private suspend fun readNotes() {
        try {
            morrow.timerVoice.readNotes(morrow.timers.ringing, sound::duck)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The Bell rings on without him.
            Log.e(TAG, "Pip couldn't read the notes", e)
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

    /** Stops the Bell, Pip, the safety stop and the notification. Which timers ring is the coordinator's business. */
    private fun leaveTimers() {
        handler.removeCallbacks(timerSafetyStop)
        readingNotes?.cancel()
        readingNotes = null
        followingTimers?.cancel()
        followingTimers = null
        quiet()
        getSystemService(NotificationManager::class.java).cancel(TimerNotifications.RINGING_ID)
    }

    /**
     * Stops the service once nothing rings and no timer is still being found; the last one found calls this again. A
     * start that came in meanwhile keeps it going, as it's newer than [lastStartId].
     */
    private fun stopIfIdle() {
        if (mode != Mode.IDLE || findingTimers > 0) return
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

    /** Buzzes on and off until [quiet]; [gentle]ly, a short, light tap every two seconds, under the games. */
    private fun startVibration(gentle: Boolean = false) {
        val pattern = if (gentle) {
            // A phone that can't vary its strength still buzzes only briefly.
            VibrationEffect.createWaveform(longArrayOf(0, 120, 1880), intArrayOf(0, GENTLE_AMPLITUDE, 0), 1)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 1)
        }
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
            removeOverlay()
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
        private const val ACTION_SNOOZE = "app.morrow.action.SNOOZE"
        private const val EXTRA_SNOOZED = "snoozed"
        private const val ACTION_RING_TIMERS = "app.morrow.action.RING_TIMERS"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L
        val RING_LIMIT = 10.minutes

        /** Of 255, how hard the vibration taps under the games. */
        private const val GENTLE_AMPLITUDE = 60

        /** How long the overlay stays after asking for the ring screen. */
        private val OVERLAY_HOLD = 2.seconds

        /** One transparent pixel that can't be touched or focused. */
        private val OverlayParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )

        /** Rings [alarmId], snoozed [snoozed] times on the way here. */
        fun start(context: Context, alarmId: Long, snoozed: Int = 0) {
            context.startForegroundService(
                Intent(context, RingService::class.java).putExtra(EXTRA_ALARM_ID, alarmId).putExtra(EXTRA_SNOOZED, snoozed),
            )
        }

        /** Snoozes the ringing alarm, if it can be. */
        fun snooze(context: Context) {
            context.startService(snoozeIntent(context))
        }

        fun snoozeIntent(context: Context): Intent = Intent(context, RingService::class.java).setAction(ACTION_SNOOZE)

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

/** What RingService does once it has found which timers have ended. */
internal enum class AfterFinding { WAIT, STOP, RING, FOLLOW }

/**
 * Given whether an [alarm] is ringing, whether the [bell] is ringing for timers, whether any timers are [ringing] now,
 * and whether RingService is [following] them already.
 */
internal fun afterFindingTimers(alarm: Boolean, bell: Boolean, ringing: Boolean, following: Boolean): AfterFinding = when {
    // The timers wait behind it.
    alarm -> AfterFinding.WAIT
    // Stopped from a notification just as it ended, say, or waiting behind the Morning screen.
    !ringing -> AfterFinding.STOP
    // The Bell stopped meanwhile, for the timers ringing before, or the Morning screen let the timers go.
    !bell -> AfterFinding.RING
    !following -> AfterFinding.FOLLOW
    else -> AfterFinding.WAIT
}
