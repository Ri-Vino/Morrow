package app.morrow.platform

import app.morrow.domain.WakeSound
import kotlin.time.Duration

/** The alarm's sound on the alarm stream, rising from quiet to full over [ramp]. */
interface AlarmSound {
    /** Starts the sound, or does nothing if it's already playing. */
    fun play(sound: WakeSound, ramp: Duration)

    fun stop()
}
