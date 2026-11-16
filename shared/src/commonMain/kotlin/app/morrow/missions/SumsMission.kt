package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class Sum(val question: String, val answer: Int)

/**
 * A sum that takes a moment's thought at [difficulty]. Answers are always positive.
 * Never the same question as [previous].
 */
fun randomSum(random: Random, difficulty: Difficulty = Difficulty.NORMAL, previous: Sum? = null): Sum {
    var sum = generateSum(random, difficulty)
    var tries = 0
    while (sum.question == previous?.question && tries++ < 8) sum = generateSum(random, difficulty)
    return sum
}

private fun generateSum(random: Random, difficulty: Difficulty): Sum = when (difficulty) {
    // Small additions and subtractions, all under 30.
    Difficulty.GENTLE -> if (random.nextBoolean()) {
        val a = 2 + random.nextInt(19)
        val b = 2 + random.nextInt(28 - a)
        if (random.nextBoolean()) Sum("$a + $b", a + b) else Sum("$b + $a", a + b)
    } else {
        val a = 6 + random.nextInt(24)
        val b = 2 + random.nextInt(a - 2)
        Sum("$a − $b", a - b)
    }
    Difficulty.NORMAL -> when (random.nextInt(5)) {
        0 -> {
            val a = 12 + random.nextInt(78)
            val b = 11 + random.nextInt(60)
            Sum("$a + $b", a + b)
        }
        1 -> {
            val a = 40 + random.nextInt(60)
            val b = 11 + random.nextInt(a - 15)
            Sum("$a − $b", a - b)
        }
        2 -> {
            val a = 3 + random.nextInt(10)
            val b = 3 + random.nextInt(10)
            Sum("$a × $b", a * b)
        }
        3 -> {
            val a = 11 + random.nextInt(9)
            val b = 3 + random.nextInt(7)
            Sum("$a × $b", a * b)
        }
        else -> {
            val a = 15 + random.nextInt(46)
            val b = 10 + random.nextInt(31)
            val c = 5 + random.nextInt(minOf(a + b, 45) - 5)
            Sum("$a + $b − $c", a + b - c)
        }
    }
    Difficulty.TOUGH -> when (random.nextInt(5)) {
        0 -> {
            val a = 120 + random.nextInt(380)
            val b = 40 + random.nextInt(160)
            Sum("$a + $b", a + b)
        }
        1 -> {
            val a = 120 + random.nextInt(480)
            val b = 40 + random.nextInt(minOf(a - 40, 200))
            Sum("$a − $b", a - b)
        }
        2 -> {
            val a = 6 + random.nextInt(10)
            val b = 6 + random.nextInt(10)
            Sum("$a × $b", a * b)
        }
        3 -> {
            val a = 13 + random.nextInt(13)
            val b = 6 + random.nextInt(4)
            Sum("$a × $b", a * b)
        }
        else -> {
            val a = 120 + random.nextInt(280)
            val b = 30 + random.nextInt(121)
            val c = 20 + random.nextInt(80)
            Sum("$a + $b − $c", a + b - c)
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
            current.copy(solved = solved, answer = "", wrong = 0, sum = if (solved < current.count) randomSum(random, difficulty, current.sum) else current.sum)
        } else {
            current.copy(answer = "", wrong = current.wrong + 1)
        }
    }

    private companion object {
        const val MAX_DIGITS = 4
    }
}
