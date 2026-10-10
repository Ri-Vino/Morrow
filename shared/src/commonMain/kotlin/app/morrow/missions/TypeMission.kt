package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class TypeState(
    val sentence: String,
    /** What's in the field, as typed. */
    val typed: String,
    /** Sentences typed so far. */
    val finished: Int,
    val rounds: Int,
) : MissionState {
    private val tidy: String = tidyTyped(typed)

    /** How many characters of [sentence] have been typed correctly, ignoring case. */
    val matched: Int = tidy.commonPrefixWith(sentence, ignoreCase = true).length

    private val mistyped = tidy.length > matched

    /** The word to check, without punctuation, once what's typed has stopped matching. */
    val typoWord: String? = if (mistyped) wordAt(sentence, matched) else null

    override val progress: Float
        get() = when {
            finished >= rounds -> 1f
            mistyped -> finished.toFloat() / rounds
            else -> (finished + matched.toFloat() / sentence.length) / rounds
        }
}

/** Copy a sentence into the field. Case, repeated spaces and the final full stop don't matter. */
class TypeMission(private val rounds: Int, difficulty: Difficulty, private val random: Random = Random) : Mission<TypeState> {

    private val pool = sentences(difficulty)
    private val used = mutableListOf<String>()

    private val mutableState = MutableStateFlow(TypeState(nextSentence(), typed = "", finished = 0, rounds = rounds))
    override val state: StateFlow<TypeState> = mutableState.asStateFlow()

    fun type(text: String) {
        val current = mutableState.value
        if (current.done) return
        if (!sameSentence(text, current.sentence)) {
            mutableState.value = current.copy(typed = text)
            return
        }
        val finished = current.finished + 1
        mutableState.value = if (finished >= rounds) {
            current.copy(typed = text, finished = finished)
        } else {
            current.copy(sentence = nextSentence(), typed = "", finished = finished)
        }
    }

    private fun nextSentence(): String {
        val fresh = pool.filter { it !in used }.ifEmpty { pool.filter { it != used.lastOrNull() } }
        return fresh.random(random).also { used += it }
    }
}

/** Lower case, with runs of spaces made one and none at the start. A space at the end stays, as a word may follow it. */
private fun tidyTyped(text: String): String = text.lowercase().replace(Spaces, " ").trimStart()

private fun sameSentence(typed: String, sentence: String) =
    tidyTyped(typed).trim().removeSuffix(".") == sentence.lowercase().removeSuffix(".")

private fun wordAt(sentence: String, index: Int): String {
    if (index >= sentence.length) return wordAt(sentence, sentence.length - 1)
    // A stray character after a finished word points back at that word.
    val at = if (sentence[index] == ' ' && index > 0) index - 1 else index
    val start = sentence.lastIndexOf(' ', at) + 1
    val end = sentence.indexOf(' ', at).let { if (it < 0) sentence.length else it }
    return sentence.substring(start, end).trim { !it.isLetterOrDigit() }
}

private val Spaces = Regex(" {2,}")
