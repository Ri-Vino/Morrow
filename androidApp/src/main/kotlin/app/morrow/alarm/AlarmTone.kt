package app.morrow.alarm

import androidx.core.net.toUri
import app.morrow.R
import app.morrow.domain.WakeSound
import app.morrow.platform.AlarmSound
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The alarm sound, ramping up from quiet, or the timers' Bell, on the alarm stream so they play with media muted.
 * [duck] lowers it while Pip speaks over it.
 */
class AlarmTone(private val context: Context) : AlarmSound {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var rampStartedAt = 0L
    private var rampMillis = 60_000L
    private var rampVolume = 1f
    private var duckVolume = 1f
    private var duckTarget = 1f
    private var levelVolume = 1f
    private var levelTarget = 1f

    private val rampStep = object : Runnable {
        override fun run() {
            val progress = ((System.currentTimeMillis() - rampStartedAt) / rampMillis.toFloat()).coerceIn(0f, 1f)
            // Quadratic, because loudness is perceived roughly logarithmically.
            rampVolume = MIN_VOLUME + (1f - MIN_VOLUME) * progress * progress
            applyVolume()
            if (progress < 1f) handler.postDelayed(this, 1.seconds.inWholeMilliseconds)
        }
    }

    private val duckStep = object : Runnable {
        override fun run() {
            val step = (1f - DUCKED_VOLUME) * DUCK_STEP_MILLIS / DUCK_FADE_MILLIS
            duckVolume = if (duckTarget < duckVolume) maxOf(duckTarget, duckVolume - step) else minOf(duckTarget, duckVolume + step)
            applyVolume()
            if (duckVolume != duckTarget) handler.postDelayed(this, DUCK_STEP_MILLIS)
        }
    }

    private val levelStep = object : Runnable {
        override fun run() {
            val step = LEVEL_STEP_MILLIS.toFloat() / LEVEL_FADE_MILLIS
            levelVolume = if (levelTarget < levelVolume) maxOf(levelTarget, levelVolume - step) else minOf(levelTarget, levelVolume + step)
            applyVolume()
            if (levelVolume != levelTarget) handler.postDelayed(this, LEVEL_STEP_MILLIS)
        }
    }

    override fun play(sound: WakeSound, ramp: Duration) = start(listOf(bundled(sound.resource)), ramp)

    override fun playFile(path: String, fallback: WakeSound, ramp: Duration) = start(listOf(File(path).toUri(), bundled(fallback.resource)), ramp)

    /** Call on the main thread. The last-resort tone can't be turned down. */
    override fun setLevel(level: Float) {
        levelTarget = level.coerceIn(0f, 1f)
        handler.removeCallbacks(levelStep)
        if (levelVolume != levelTarget) handler.post(levelStep)
    }

    /** The timers' Bell, at full volume straight away: a timer that has ended has no time to ramp up. */
    fun playBell() = start(listOf(bundled(R.raw.bell)), Duration.ZERO)

    /**
     * Fades the sound down to a murmur while Pip speaks over it ([ducked]), and back up after. Call on the main thread.
     * The last-resort tone can't be ducked.
     */
    fun duck(ducked: Boolean) {
        duckTarget = if (ducked) DUCKED_VOLUME else 1f
        handler.removeCallbacks(duckStep)
        if (duckVolume != duckTarget) handler.post(duckStep)
    }

    private fun applyVolume() {
        val volume = rampVolume * duckVolume * levelVolume
        player?.setVolume(volume, volume)
    }

    private fun start(first: List<Uri>, ramp: Duration) {
        if (player != null || tone != null) return
        rampMillis = ramp.inWholeMilliseconds.coerceAtLeast(1)
        val startVolume = if (ramp.isPositive()) MIN_VOLUME else 1f
        rampVolume = startVolume
        for (uri in (first + phoneAlarmSounds()).distinct()) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AlarmAudio)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    setVolume(startVolume * duckVolume * levelVolume, startVolume * duckVolume * levelVolume)
                    start()
                }
            }
            started.onSuccess {
                player = it
                rampStartedAt = System.currentTimeMillis()
                handler.post(rampStep)
                return
            }
            Log.w(TAG, "Couldn't play $uri", started.exceptionOrNull())
        }
        // Last resort, so an alarm is never silent.
        tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).apply {
            startTone(ToneGenerator.TONE_SUP_RINGTONE)
        }
    }

    override fun stop() {
        try {
            handler.removeCallbacks(rampStep)
            handler.removeCallbacks(duckStep)
            handler.removeCallbacks(levelStep)
            player?.let { runCatching { it.stop() }; it.release() }
            tone?.release()
        } finally {
            player = null
            tone = null
            duckVolume = 1f
            duckTarget = 1f
            levelVolume = 1f
            levelTarget = 1f
        }
    }

    private fun bundled(resource: Int): Uri = "android.resource://${context.packageName}/$resource".toUri()

    /** The phone's own alarm sounds, in case none of the app's will play. */
    private fun phoneAlarmSounds(): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    )

    companion object {
        private const val TAG = "AlarmTone"
        private const val MIN_VOLUME = 0.05f

        /** About −14 dB: still heard, but well under Pip's voice. */
        private const val DUCKED_VOLUME = 0.2f
        private const val DUCK_FADE_MILLIS = 300L
        private const val DUCK_STEP_MILLIS = 20L
        private const val LEVEL_FADE_MILLIS = 600L
        private const val LEVEL_STEP_MILLIS = 20L

        val AlarmAudio: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

private val WakeSound.resource: Int
    get() = when (this) {
        WakeSound.SOFT_PIANO -> R.raw.soft_piano
        WakeSound.WIND_CHIMES -> R.raw.wind_chimes
        WakeSound.MUSIC_BOX -> R.raw.music_box
        WakeSound.SINGING_BOWL -> R.raw.singing_bowl
        WakeSound.BIRDSONG -> R.raw.birdsong
        WakeSound.OCEAN_WAVES -> R.raw.ocean_waves
        WakeSound.GENTLE_RAIN -> R.raw.gentle_rain
        WakeSound.MARIMBA -> R.raw.marimba
        WakeSound.SUNRISE_ARPEGGIO -> R.raw.sunrise_arpeggio
        WakeSound.ALARM_BELL -> R.raw.alarm_bell
        WakeSound.DIGITAL_BEEPS -> R.raw.digital_beeps
    }
