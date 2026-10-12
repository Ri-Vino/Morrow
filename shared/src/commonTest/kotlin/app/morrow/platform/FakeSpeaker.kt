package app.morrow.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** Remembers what it was asked to say, and says nothing. Each [speak] takes [talkFor] of virtual time, unless [stop] cuts it off. */
class FakeSpeaker(private val installed: List<VoiceInfo> = emptyList(), private val talkFor: Duration = Duration.ZERO) : Speaker {
    val spoken = mutableListOf<Pair<String, SpeechPurpose>>()
    var stops = 0
        private set
    var voice: String? = null
        private set
    private var talking: CompletableDeferred<Unit>? = null

    override suspend fun speak(text: String, purpose: SpeechPurpose) {
        spoken += text to purpose
        if (!talkFor.isPositive()) return
        val cutOff = CompletableDeferred<Unit>().also { talking = it }
        withTimeoutOrNull(talkFor) { cutOff.await() }
    }

    override fun stop() {
        stops++
        talking?.complete(Unit)
    }

    override suspend fun voices(): List<VoiceInfo> = installed

    override fun setVoice(id: String?) {
        voice = id
    }
}
