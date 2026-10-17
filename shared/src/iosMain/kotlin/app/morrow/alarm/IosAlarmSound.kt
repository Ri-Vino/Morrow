@file:OptIn(ExperimentalForeignApi::class)

package app.morrow.alarm

import app.morrow.domain.WakeSound
import app.morrow.platform.AlarmSound
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** The alarm sound while Morrow is open: in previews, and during a mission after the system's alarm has stopped. */
class IosAlarmSound : AlarmSound {

    private var player: AVAudioPlayer? = null

    override fun play(sound: WakeSound, ramp: Duration) {
        if (player != null) return
        val url = NSBundle.mainBundle.URLForResource(sound.fileName.substringBeforeLast('.'), withExtension = "caf") ?: return
        // Playback, so the ring/silent switch doesn't mute it, as it doesn't mute the system's alarms.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
        player = AVAudioPlayer(contentsOfURL = url, error = null).apply {
            numberOfLoops = -1
            volume = MIN_VOLUME
            play()
            setVolume(1f, fadeDuration = ramp.toDouble(DurationUnit.SECONDS))
        }
    }

    override fun setLevel(level: Float) {
        player?.setVolume(level.coerceIn(0f, 1f), fadeDuration = LEVEL_FADE_SECONDS)
    }

    override fun stop() {
        val playing = player ?: return
        playing.stop()
        player = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    private companion object {
        const val MIN_VOLUME = 0.05f
        const val LEVEL_FADE_SECONDS = 0.6
    }
}
