package app.morrow.platform

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.time.Duration.Companion.seconds

/**
 * Pip's voice through the phone's own text-to-speech engine, which is started on first use and released
 * [IDLE_RELEASE] after the last use. The words go to the phone's engine and nowhere else. With no engine, or one that
 * won't start in time, nothing is spoken and every call returns at once. Pip only uses an installed voice that works
 * offline (see [voiceToUse]); with none, he says nothing.
 *
 * A [VoiceInfo]'s [VoiceInfo.language] is the voice's language and region as the phone reads them ("English (UK)"), and
 * its [VoiceInfo.name] is what the picker shows.
 */
class AndroidSpeaker internal constructor(
    private val factory: EngineFactory,
    private val main: CoroutineDispatcher,
    private val phoneLocale: () -> Locale = Locale::getDefault,
) : Speaker {

    constructor(context: Context) : this(TextToSpeechFactory(context.applicationContext), Dispatchers.Main.immediate)

    private class Utterance(val id: String, val purpose: SpeechPurpose, val onStart: () -> Unit) {
        val done = CompletableDeferred<Unit>()
        var started = false
    }

    private val scope = CoroutineScope(SupervisorJob() + main)

    // Everything below is used on the main thread, except that stop() and the engine's callbacks may come from anywhere.
    @Volatile private var engine: SpeechEngine? = null
    @Volatile private var current: Utterance? = null
    private val stopped = AtomicIntegerArray(SpeechPurpose.entries.size)
    @Volatile private var chosenVoice: String? = null
    private var starting: CompletableDeferred<SpeechEngine?>? = null
    private var releasing: Job? = null
    private var uses = 0
    private var utterances = 0

    override suspend fun speak(text: String, purpose: SpeechPurpose, onStart: () -> Unit) {
        if (text.isBlank()) return
        val stopsBefore = stopped[purpose.ordinal]
        withContext(main) {
            withEngine { engine ->
                // A stop() while the engine was starting meant this wasn't wanted any more.
                if (stopped[purpose.ordinal] != stopsBefore) return@withEngine
                if (current?.purpose == SpeechPurpose.TIMER_NOTE && purpose != SpeechPurpose.TIMER_NOTE) return@withEngine
                val voice = voiceToUse(engine.voices(), engine.defaultVoice(), chosenVoice, phoneLocale()) ?: return@withEngine
                engine.useVoice(voice)
                val utterance = Utterance("morrow-${++utterances}", purpose, onStart)
                current?.done?.complete(Unit)
                current = utterance
                try {
                    if (engine.speak(text, utterance.id)) {
                        withTimeoutOrNull(SPEECH_LIMIT) { utterance.done.await() }
                    } else {
                        // The engine has died or is stuck; start a fresh one next time.
                        utterance.done.complete(Unit)
                        drop(engine)
                    }
                } finally {
                    if (current === utterance) {
                        // Cancelled or timed out rather than finished.
                        if (!utterance.done.isCompleted) engine.stop()
                        current = null
                    }
                }
            }
        }
    }

    override fun stop(purpose: SpeechPurpose) {
        stopped.incrementAndGet(purpose.ordinal)
        val speaking = current?.takeIf { it.purpose == purpose } ?: return
        speaking.done.complete(Unit)
        engine?.stop()
    }

    override suspend fun voices(): List<VoiceInfo> = withContext(main) {
        withEngine { installedVoices(it.voices(), phoneLocale()) } ?: emptyList()
    }

    /** Takes effect from the next words. */
    override fun setVoice(id: String?) {
        chosenVoice = id
    }

    /** Runs [block] with a started engine and keeps the engine alive meanwhile; null when there is none. */
    private suspend fun <T> withEngine(block: suspend (SpeechEngine) -> T): T? {
        uses++
        releasing?.cancel()
        try {
            val ready = ready() ?: return null
            return block(ready)
        } finally {
            uses--
            releaseWhenIdle()
        }
    }

    private suspend fun ready(): SpeechEngine? {
        engine?.let { return it }
        val pending = starting ?: start()
        // A slow engine keeps starting after this gives up, and is released if nothing else wants it.
        return withTimeoutOrNull(START_LIMIT) { pending.await() }
    }

    private fun start(): CompletableDeferred<SpeechEngine?> {
        val pending = CompletableDeferred<SpeechEngine?>()
        starting = pending
        factory.start(onUtteranceStart = ::utteranceStarted, onUtteranceEnd = ::utteranceEnded) { started ->
            starting = null
            if (started != null) {
                engine = started
                releaseWhenIdle()
            }
            pending.complete(started)
        }
        return pending
    }

    private fun utteranceStarted(id: String) {
        scope.launch {
            val utterance = current?.takeIf { it.id == id && !it.started && !it.done.isCompleted } ?: return@launch
            utterance.started = true
            utterance.onStart()
        }
    }

    private fun utteranceEnded(id: String) {
        current?.takeIf { it.id == id }?.done?.complete(Unit)
    }

    private fun releaseWhenIdle() {
        if (uses > 0 || engine == null) return
        releasing?.cancel()
        releasing = scope.launch {
            delay(IDLE_RELEASE)
            engine?.let(::drop)
        }
    }

    private fun drop(dropped: SpeechEngine) {
        if (engine === dropped) engine = null
        dropped.shutdown()
    }

    companion object {
        /** How long after it was last used the engine is released. */
        val IDLE_RELEASE = 30.seconds

        /** How long the first words wait for the engine to start before giving up. */
        val START_LIMIT = 5.seconds

        /** A ceiling on one utterance, in case the engine dies without saying so. */
        val SPEECH_LIMIT = 60.seconds
    }
}
