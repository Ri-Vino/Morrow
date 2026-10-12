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

    /** Speaks with the voice called [name] from now on, or the engine's default when null or unknown. */
    fun useVoice(name: String?)

    /** Cuts off whatever is being said and starts saying [text] as [id]. False if the engine refused. */
    fun speak(text: String, id: String): Boolean

    fun stop()

    fun shutdown()
}

/** Starts engines. */
internal interface EngineFactory {
    /**
     * Starts an engine and calls [onStarted] on the main thread with it once ready, or with null when there is none or
     * it failed. [onUtteranceEnd] hears the id of each utterance that finishes, fails or is stopped.
     */
    fun start(onUtteranceEnd: (id: String) -> Unit, onStarted: (SpeechEngine?) -> Unit)
}
