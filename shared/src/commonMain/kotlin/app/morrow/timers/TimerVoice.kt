package app.morrow.timers

import app.morrow.platform.Speaker
import app.morrow.platform.SpeechPurpose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

/**
 * Pip reading out the notes of the timers that are ringing, over their chime: [FIRST_READING] after they start ringing,
 * then again [BETWEEN_READINGS] after each reading ends, [READINGS] times in all. Timers without a note aren't read.
 * With several notes he reads them all in one go, in the order the timers ended.
 *
 * Whenever the notes that are ringing change, Pip stops mid-sentence if he's reading and starts over with the notes
 * there are now. So a timer that ends while others ring gets its three readings, beginning 2 s after it ended, with the
 * others' notes read beside it. A timer stopped on its own is never read again, and the rest start over. A timer
 * without a note coming or going changes nothing.
 */
class TimerVoice(private val speaker: Speaker) {
    private val speakingState = MutableStateFlow(false)

    /** True while Pip is reading notes out, so the Timer done screen can show him talking. */
    val speaking: StateFlow<Boolean> = speakingState.asStateFlow()

    /**
     * Reads the notes of [ringing] as above, until cancelled. Cancelling stops him at once, so this ends with the
     * ringing. [duck] lowers the chime with true just before each reading, and brings it back with false after.
     */
    suspend fun readNotes(ringing: Flow<List<Timer>>, duck: (Boolean) -> Unit) {
        ringing
            .map { timers -> timers.filter { it.note.isNotBlank() }.map { it.id to it.note.trim() } }
            .distinctUntilChanged()
            .collectLatest { notes ->
                if (notes.isEmpty()) return@collectLatest
                val text = spokenNotes(notes.map { it.second })
                delay(FIRST_READING)
                repeat(READINGS) { reading ->
                    if (reading > 0) delay(BETWEEN_READINGS)
                    read(text, duck)
                }
            }
    }

    /** Cuts off the reading under way, if any: for Stop and "+1 minute", before the timers themselves have stopped. */
    fun hush() {
        if (speakingState.value) speaker.stop()
    }

    private suspend fun read(text: String, duck: (Boolean) -> Unit) {
        var finished = false
        speakingState.value = true
        duck(true)
        try {
            speaker.speak(text, SpeechPurpose.TIMER_NOTE)
            finished = true
        } finally {
            if (!finished) speaker.stop()
            duck(false)
            speakingState.value = false
        }
    }

    companion object {
        val FIRST_READING = 2.seconds
        val BETWEEN_READINGS = 30.seconds
        const val READINGS = 3
    }
}

/** The notes as Pip reads them: one as it is, several as sentences one after another. */
internal fun spokenNotes(notes: List<String>): String =
    notes.singleOrNull() ?: notes.joinToString(" ") { note -> if (note.last() in ".!?…") note else "$note." }
