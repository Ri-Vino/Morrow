package app.morrow.platform

import java.util.Locale

/** A voice as the phone's text-to-speech engine reports it. */
internal data class EngineVoice(
    val name: String,
    val locale: Locale,
    val quality: Int,
    val needsNetwork: Boolean,
    val installed: Boolean,
)

/** What [AndroidSpeaker] needs of a started text-to-speech engine, so tests can stand one in. */
internal interface SpeechEngine {
    /** Every voice the engine offers, before any filtering. */
    fun voices(): List<EngineVoice>

    /** The voice the engine speaks with unless told otherwise, if it says. */
    fun defaultVoice(): EngineVoice?

    /** Speaks with the voice called [name] from now on. */
    fun useVoice(name: String)

    /** Cuts off whatever is being said and starts saying [text] as [id]. False if the engine refused. */
    fun speak(text: String, id: String): Boolean

    fun stop()

    fun shutdown()
}

/** Starts engines. */
internal interface EngineFactory {
    /**
     * Starts an engine and calls [onStarted] on the main thread with it once ready, or with null when there is none or
     * it failed. [onUtteranceStart] hears the id of each utterance as its words begin, and [onUtteranceEnd] as it
     * finishes, fails or is stopped; both may be called on any thread.
     */
    fun start(onUtteranceStart: (id: String) -> Unit, onUtteranceEnd: (id: String) -> Unit, onStarted: (SpeechEngine?) -> Unit)
}
