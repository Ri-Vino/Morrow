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
import app.morrow.platform.AlarmSound
import kotlin.time.Duration.Companion.seconds

/** The alarm sound on the alarm stream, so it plays with media muted, ramping up from quiet over [RAMP]. */
class AlarmTone(private val context: Context) : AlarmSound {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var rampStartedAt = 0L

    private val ramp = object : Runnable {
        override fun run() {
            val progress = ((System.currentTimeMillis() - rampStartedAt) / RAMP.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
            // Quadratic, because loudness is perceived roughly logarithmically.
            val volume = MIN_VOLUME + (1f - MIN_VOLUME) * progress * progress
            player?.setVolume(volume, volume)
            if (progress < 1f) handler.postDelayed(this, 1.seconds.inWholeMilliseconds)
        }
    }

    override fun play() {
        if (player != null || tone != null) return
        for (uri in soundCandidates()) {
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

    override fun stop() {
        try {
            handler.removeCallbacks(ramp)
            player?.let { runCatching { it.stop() }; it.release() }
            tone?.release()
        } finally {
            player = null
            tone = null
        }
    }

    private fun soundCandidates(): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    ).distinct()

    companion object {
        private const val TAG = "AlarmTone"
        private const val MIN_VOLUME = 0.05f
        private val RAMP = 60.seconds

        val AlarmAudio: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
