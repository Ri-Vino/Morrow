package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class UnscrambleState(
    /** The sentence's words in the right order. */
    val words: List<String>,
    /** The word chips as shown: `order[i]` is the place in [words] of the chip at index `i`. */
    val order: List<Int>,
    /** How many words, from the start of the sentence, are in place. */
    val placed: Int,
    /** Wrong taps on this sentence. */
    val wrong: Int,
    /** The chip last tapped wrongly, until a right one is tapped. */
    val wrongIndex: Int?,
    /** Sentences finished so far. */
    val finished: Int,
    val rounds: Int,
) : MissionState {
    /** The chips in the order shown. */
    val bank: List<String> get() = order.map { words[it] }

    override val progress: Float
        get() = if (finished >= rounds) 1f else (finished + placed.toFloat() / words.size) / rounds
}

/** Tap a sentence's words back into order. */
class UnscrambleMission(private val rounds: Int, difficulty: Difficulty, private val random: Random = Random) : Mission<UnscrambleState> {

    private val pool = scrambleSentences(difficulty)
    private val used = mutableListOf<String>()

    private val mutableState = MutableStateFlow(deal(finished = 0))
    override val state: StateFlow<UnscrambleState> = mutableState.asStateFlow()

    /** Taps the chip at [index] in [UnscrambleState.bank]. A wrong one costs nothing. */
    fun pick(index: Int) {
        val current = mutableState.value
        if (current.done || index !in current.order.indices || current.order[index] < current.placed) return
        mutableState.value = when {
            current.order[index] != current.placed -> current.copy(wrong = current.wrong + 1, wrongIndex = index)
            current.placed + 1 < current.words.size -> current.copy(placed = current.placed + 1, wrongIndex = null)
            current.finished + 1 >= rounds -> current.copy(placed = current.words.size, wrongIndex = null, finished = rounds)
            else -> deal(current.finished + 1)
        }
    }

    private fun deal(finished: Int): UnscrambleState {
        val fresh = pool.filter { it !in used }.ifEmpty { pool.filter { it != used.lastOrNull() } }
        val words = wordsOf(fresh.random(random).also { used += it })
        var order = words.indices.shuffled(random)
        while (order == words.indices.toList()) order = words.indices.shuffled(random)
        return UnscrambleState(words, order, placed = 0, wrong = 0, wrongIndex = null, finished = finished, rounds = rounds)
    }
}
