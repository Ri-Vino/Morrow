package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class Sum(val question: String, val answer: Int)

/** A sum that takes a moment's thought at [difficulty]. Answers are always positive. */
fun randomSum(random: Random, difficulty: Difficulty = Difficulty.NORMAL): Sum = when (difficulty) {
    // Small additions and subtractions, all under 30.
    Difficulty.GENTLE -> {
        val a = 2 + random.nextInt(15)
        val b = 2 + random.nextInt(9)
        if (random.nextBoolean()) Sum("$a + $b", a + b) else Sum("${a + b} − $b", a)
    }
    Difficulty.NORMAL -> when (random.nextInt(3)) {
        0 -> {
            val a = 14 + random.nextInt(60)
            val b = 12 + random.nextInt(50)
            Sum("$a + $b", a + b)
        }
        1 -> {
            val a = 50 + random.nextInt(45)
            val b = 11 + random.nextInt(35)
            Sum("$a − $b", a - b)
        }
        else -> {
            val a = 4 + random.nextInt(9)
            val b = 3 + random.nextInt(8)
            Sum("$a × $b", a * b)
        }
    }
    Difficulty.TOUGH -> when (random.nextInt(3)) {
        0 -> {
            val a = 120 + random.nextInt(300)
            val b = 40 + random.nextInt(80)
            Sum("$a + $b", a + b)
        }
        1 -> {
            val a = 120 + random.nextInt(300)
            val b = 40 + random.nextInt(80)
            Sum("$a − $b", a - b)
        }
        else -> {
            val a = 6 + random.nextInt(9)
            val b = 6 + random.nextInt(9)
            Sum("$a × $b", a * b)
        }
    }
}

data class SumsState(
    val sum: Sum,
    val solved: Int,
    val count: Int,
    /** What's been typed so far. */
    val answer: String,
    /** Wrong answers to the current sum. */
    val wrong: Int,
) : MissionState {
    override val progress: Float get() = solved.toFloat() / count
}

class SumsMission(
    count: Int,
    private val random: Random = Random,
    private val difficulty: Difficulty = Difficulty.NORMAL,
) : Mission<SumsState> {

    private val mutableState = MutableStateFlow(SumsState(randomSum(random, difficulty), solved = 0, count = count, answer = "", wrong = 0))
    override val state: StateFlow<SumsState> = mutableState.asStateFlow()

    fun type(digit: Int) = mutableState.update {
        if (it.done || it.answer.length >= MAX_DIGITS) it else it.copy(answer = it.answer + digit)
    }

    fun delete() = mutableState.update { if (it.done) it else it.copy(answer = it.answer.dropLast(1)) }

    fun check() {
        val current = state.value
        if (current.done || current.answer.isEmpty()) return
        mutableState.value = if (current.answer.toInt() == current.sum.answer) {
            val solved = current.solved + 1
            current.copy(solved = solved, answer = "", wrong = 0, sum = if (solved < current.count) randomSum(random, difficulty) else current.sum)
        } else {
            current.copy(answer = "", wrong = current.wrong + 1)
        }
    }

    private companion object {
        const val MAX_DIGITS = 4
    }
}
