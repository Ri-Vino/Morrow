package app.morrow.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Remembers what it was asked to say, and says nothing. Each [speak] takes [talkFor] of virtual time, unless [stop] for
 * its purpose, or newer speech, cuts it off. Like the real ones, a timer's note is never cut off by anything else, and
 * speech asked for meanwhile is skipped, not [spoken]. With [silent], it plays a phone whose engine never starts: every
 * call returns at once, and nothing ever starts.
 */
class FakeSpeaker(
    private val installed: List<VoiceInfo> = emptyList(),
    private val talkFor: Duration = Duration.ZERO,
    var silent: Boolean = false,
) : Speaker {
    val spoken = mutableListOf<Pair<String, SpeechPurpose>>()

    /** Every [stop], by purpose, whether or not anything was being said for it. */
    val stopped = mutableListOf<SpeechPurpose>()
    val stops: Int get() = stopped.size
    var voice: String? = null
        private set

    private class Talk(val purpose: SpeechPurpose, val cutOff: CompletableDeferred<Unit> = CompletableDeferred())

    private var talking: Talk? = null

    /** What's being said now, if anything. */
    val talkingFor: SpeechPurpose? get() = talking?.purpose

    override suspend fun speak(text: String, purpose: SpeechPurpose, onStart: () -> Unit) {
        if (silent) return
        val before = talking
        if (before != null && before.purpose == SpeechPurpose.TIMER_NOTE && purpose != SpeechPurpose.TIMER_NOTE) return
        spoken += text to purpose
        before?.cutOff?.complete(Unit)
        if (!talkFor.isPositive()) {
            talking = null
            onStart()
            return
        }
        val talk = Talk(purpose).also { talking = it }
        onStart()
        try {
            withTimeoutOrNull(talkFor) { talk.cutOff.await() }
        } finally {
            if (talking === talk) talking = null
        }
    }

    override fun stop(purpose: SpeechPurpose) {
        stopped += purpose
        talking?.takeIf { it.purpose == purpose }?.cutOff?.complete(Unit)
    }

    override suspend fun voices(): List<VoiceInfo> = installed

    override fun setVoice(id: String?) {
        voice = id
    }
}
