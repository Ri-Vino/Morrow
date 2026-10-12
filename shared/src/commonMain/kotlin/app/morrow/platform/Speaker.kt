package app.morrow.platform

/** Why Pip is speaking. */
enum class SpeechPurpose {
    /** A timer's note, over its chime. */
    TIMER_NOTE,

    /** The Morning screen's weather. */
    MORNING_WEATHER,

    /** Settings' "Hear a sample". */
    SAMPLE,
}

/** An installed voice that works offline. [id] stays the same across launches, and is what Settings stores. */
data class VoiceInfo(val id: String, val name: String, val language: String)

/** Pip's voice: the phone's own text-to-speech. Nothing is sent anywhere. */
interface Speaker {
    /**
     * Says [text], cutting off anything still being said, and returns once it has been said or [stop] cut it off. A
     * timer's note is the exception: nothing else cuts it off, so speech for another [purpose] asked for while one is
     * being read is skipped and returns at once. [onStart] runs on the main thread once the words actually begin, which
     * may be never. Returns at once when the phone has nothing to speak with; the text stays on screen either way.
     */
    suspend fun speak(text: String, purpose: SpeechPurpose, onStart: () -> Unit = {})

    /**
     * Stops speech for [purpose] straight away, including speech still waiting for the engine to start. Speech for any
     * other purpose carries on, so one screen can't cut off what another is saying.
     */
    fun stop(purpose: SpeechPurpose)

    /** The voices for the phone's language that don't need the network, best first. Empty when there's no engine. */
    suspend fun voices(): List<VoiceInfo>

    /** Speaks with the voice [id] from now on, or the phone's default when null or no longer installed. */
    fun setVoice(id: String?)
}

/** Says nothing: for a phone with no text-to-speech, and for tests. */
object NoSpeaker : Speaker {
    override suspend fun speak(text: String, purpose: SpeechPurpose, onStart: () -> Unit) = Unit

    override fun stop(purpose: SpeechPurpose) = Unit

    override suspend fun voices(): List<VoiceInfo> = emptyList()

    override fun setVoice(id: String?) = Unit
}
