package app.morrow.platform

import app.morrow.domain.WakeSound
import kotlin.time.Duration

/** The alarm's sound on the alarm stream, rising from quiet to full over [ramp]. */
interface AlarmSound {
    /** Starts the sound, or does nothing if it's already playing. */
    fun play(sound: WakeSound, ramp: Duration)

    fun stop()

    /**
     * Fades the sound to [level] of its volume, from 0, silent, to 1, as loud as it rings. It goes back to 1 when the
     * sound stops. Used while the alarm's games are played.
     */
    fun setLevel(level: Float) {}
}
