package app.morrow.platform

import app.morrow.domain.OwnSound
import app.morrow.domain.RingSound
import app.morrow.domain.WakeSound
import kotlin.time.Duration

/** The alarm's sound on the alarm stream, rising from quiet to full over [ramp]. */
interface AlarmSound {
    /** Starts the sound, or does nothing if it's already playing. */
    fun play(sound: WakeSound, ramp: Duration)

    /** Like [play], for one of the user's own sounds at [path]; [fallback] plays if it won't. */
    fun playFile(path: String, fallback: WakeSound, ramp: Duration) = play(fallback, ramp)

    fun stop()

    /**
     * Fades the sound to [level] of its volume, from 0, silent, to 1, as loud as it rings. It goes back to 1 when the
     * sound stops. Used while the alarm's games are played.
     */
    fun setLevel(level: Float) {}
}

/** Plays [sound], looking the user's own up in [own]; with no sound, or one that's gone, it plays [fallback]. */
fun AlarmSound.play(sound: RingSound?, fallback: WakeSound, ramp: Duration, own: List<OwnSound>) {
    when (sound) {
        is RingSound.Bundled -> play(sound.sound, ramp)
        is RingSound.Own -> own.firstOrNull { it.id == sound.id }?.let { playFile(it.path, fallback, ramp) } ?: play(fallback, ramp)
        null -> play(fallback, ramp)
    }
}
