package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnscrambleMissionTest {

    private fun mission(rounds: Int = 2, difficulty: Difficulty = Difficulty.NORMAL, seed: Int = 5) =
        UnscrambleMission(rounds, difficulty, Random(seed))

    /** Taps the chip that comes next in the sentence. */
    private fun UnscrambleMission.pickNext() {
        val state = state.value
        pick(state.order.indexOf(state.placed))
    }

    @Test
    fun theWordsAreTheSentenceWithoutItsFullStop() {
        for (difficulty in Difficulty.entries) {
            val state = mission(difficulty = difficulty).state.value
            assertTrue(state.words.joinToString(" ") in scrambleSentences(difficulty))
        }
    }

    @Test
    fun theBankHoldsEveryWordAndIsNeverAlreadyInOrder() {
        for (seed in 0 until 200) {
            val state = mission(rounds = 5, difficulty = Difficulty.entries[seed % 3], seed = seed).state.value
            assertEquals(state.words.sorted(), state.bank.sorted())
            assertNotEquals(state.words, state.bank, "seed $seed dealt the sentence solved")
            assertEquals(0, state.placed)
            assertNull(state.wrongIndex)
        }
    }

    @Test
    fun theRightNextWordIsPlaced() {
        val mission = mission()
        mission.pickNext()
        assertEquals(1, mission.state.value.placed)
        mission.pickNext()
        val state = mission.state.value
        assertEquals(2, state.placed)
        assertEquals(0, state.wrong)
        assertEquals((2f / state.words.size) / 2, state.progress)
    }

    @Test
    fun aWrongWordIsCountedAndRemembered() {
        val mission = mission()
        val state = mission.state.value
        val wrong = state.order.indexOfFirst { it != state.placed }
        mission.pick(wrong)

        val after = mission.state.value
        assertEquals(0, after.placed)
        assertEquals(1, after.wrong)
        assertEquals(wrong, after.wrongIndex)
        assertEquals(0f, after.progress)

        mission.pick(wrong)
        assertEquals(2, mission.state.value.wrong)
    }

    @Test
    fun aRightWordClearsTheWrongMark() {
        val mission = mission()
        val state = mission.state.value
        mission.pick(state.order.indexOfFirst { it != state.placed })
        mission.pickNext()
        assertNull(mission.state.value.wrongIndex)
        assertEquals(1, mission.state.value.wrong)
    }

    @Test
    fun wordsAlreadyPlacedAndBadIndexesAreIgnored() {
        val mission = mission()
        mission.pickNext()
        val before = mission.state.value
        mission.pick(before.order.indexOf(0))
        mission.pick(-1)
        mission.pick(before.order.size)
        assertEquals(before, mission.state.value)
    }

    @Test
    fun finishingASentenceCountsARoundAndDealsANewOne() {
        val mission = mission(rounds = 3)
        val first = mission.state.value.words
        repeat(first.size) { mission.pickNext() }

        val state = mission.state.value
        assertEquals(1, state.finished)
        assertEquals(0, state.placed)
        assertEquals(0, state.wrong)
        assertNotEquals(first, state.words)
        assertNotEquals(state.words, state.bank)
        assertEquals(1f / 3, state.progress)
        assertFalse(state.done)
    }

    @Test
    fun finishingEveryRoundIsDoneAndLaterTapsAreIgnored() {
        val mission = mission(rounds = 2)
        repeat(2) {
            val size = mission.state.value.words.size
            repeat(size) { mission.pickNext() }
        }
        val done = mission.state.value
        assertTrue(done.done)
        assertEquals(1f, done.progress)
        assertEquals(done.words.size, done.placed)

        mission.pick(0)
        assertEquals(done, mission.state.value)
    }

    @Test
    fun wrongTapsNeverMoveProgressBackwards() {
        val mission = mission(rounds = 1)
        mission.pickNext()
        val progress = mission.state.value.progress
        val state = mission.state.value
        mission.pick(state.order.indexOfFirst { it > state.placed })
        assertEquals(progress, mission.state.value.progress)
    }
}
