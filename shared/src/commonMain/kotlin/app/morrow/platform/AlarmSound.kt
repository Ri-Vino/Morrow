package app.morrow.platform

/** The alarm's sound on the alarm stream, rising slowly from quiet, for previewing what an alarm will be like. */
interface AlarmSound {
    /** Starts the sound, or does nothing if it's already playing. */
    fun play()

    fun stop()
}
