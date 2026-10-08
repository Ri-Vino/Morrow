package app.morrow.alarm

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
import androidx.core.net.toUri
import app.morrow.R
import app.morrow.domain.WakeSound
import app.morrow.platform.AlarmSound
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The alarm sound on the alarm stream, so it plays with media muted, ramping up from quiet. */
class AlarmTone(private val context: Context) : AlarmSound {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var rampStartedAt = 0L
    private var rampMillis = 60_000L

    private val rampStep = object : Runnable {
        override fun run() {
            val progress = ((System.currentTimeMillis() - rampStartedAt) / rampMillis.toFloat()).coerceIn(0f, 1f)
            // Quadratic, because loudness is perceived roughly logarithmically.
            val volume = MIN_VOLUME + (1f - MIN_VOLUME) * progress * progress
            player?.setVolume(volume, volume)
            if (progress < 1f) handler.postDelayed(this, 1.seconds.inWholeMilliseconds)
        }
    }

    override fun play(sound: WakeSound, ramp: Duration) {
        if (player != null || tone != null) return
        rampMillis = ramp.inWholeMilliseconds.coerceAtLeast(1)
        for (uri in soundCandidates(sound)) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AlarmAudio)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    setVolume(MIN_VOLUME, MIN_VOLUME)
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
            player?.let { runCatching { it.stop() }; it.release() }
            tone?.release()
        } finally {
            player = null
            tone = null
        }
    }

    /** The chosen sound, then the phone's own alarm sound in case the bundled one won't play. */
    private fun soundCandidates(sound: WakeSound): List<Uri> = listOfNotNull(
        "android.resource://${context.packageName}/${sound.resource}".toUri(),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    ).distinct()

    companion object {
        private const val TAG = "AlarmTone"
        private const val MIN_VOLUME = 0.05f

        val AlarmAudio: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

private val WakeSound.resource: Int
    get() = when (this) {
        WakeSound.BIRDSONG -> R.raw.birdsong
        WakeSound.WIND_CHIMES -> R.raw.wind_chimes
        WakeSound.SOFT_PIANO -> R.raw.soft_piano
    }
