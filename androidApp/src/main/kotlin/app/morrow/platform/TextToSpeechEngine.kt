package app.morrow.platform

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice

/** Starts the phone's [TextToSpeech], heard on the alarm volume so Pip is heard when media is muted. */
internal class TextToSpeechFactory(private val context: Context) : EngineFactory {

    override fun start(onUtteranceEnd: (id: String) -> Unit, onStarted: (SpeechEngine?) -> Unit) {
        var created: TextToSpeech? = null
        var earlyStatus: Int? = null

        fun finish(tts: TextToSpeech, status: Int) {
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown()
                onStarted(null)
                return
            }
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.setOnUtteranceProgressListener(UtteranceEnds(onUtteranceEnd))
            onStarted(TextToSpeechEngine(tts))
        }

        // With no engine installed the callback runs inside the constructor, before `created` has a value.
        val tts = TextToSpeech(context) { status ->
            val ready = created
            if (ready == null) earlyStatus = status else finish(ready, status)
        }
        created = tts
        earlyStatus?.let { finish(tts, it) }
    }
}

private class UtteranceEnds(private val onEnd: (String) -> Unit) : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) = Unit

    override fun onDone(utteranceId: String?) {
        utteranceId?.let(onEnd)
    }

    @Deprecated("Replaced by the version that has an error code.")
    override fun onError(utteranceId: String?) {
        utteranceId?.let(onEnd)
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        utteranceId?.let(onEnd)
    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        utteranceId?.let(onEnd)
    }
}

private class TextToSpeechEngine(private val tts: TextToSpeech) : SpeechEngine {

    override fun voices(): List<EngineVoice> =
        try {
            tts.voices.orEmpty().map { it.toEngineVoice() }
        } catch (_: RuntimeException) {
            // Some engines throw rather than say they have no voices.
            emptyList()
        }

    override fun useVoice(name: String?) {
        val voice = name?.let { wanted -> tts.voices.orEmpty().firstOrNull { it.name == wanted } } ?: tts.defaultVoice
        if (voice != null) tts.setVoice(voice)
    }

    override fun speak(text: String, id: String): Boolean =
        tts.speak(text.take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun Voice.toEngineVoice() = EngineVoice(
        name = name,
        locale = locale,
        quality = quality,
        needsNetwork = isNetworkConnectionRequired,
        installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty(),
    )
}
