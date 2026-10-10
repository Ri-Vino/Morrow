package app.morrow.ui.mission

import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import kotlin.test.Test
import kotlin.test.assertEquals

class SequenceModelTest {

    private val threeGames = listOf(
        GameStep(MissionType.SUMS, 3),
        GameStep(MissionType.SCAN, 1),
        GameStep(MissionType.BREATHE, 2),
    )

    @Test
    fun pipRisesAcrossTheWholeSequence() {
        assertEquals(0f, sequenceProgress(finished = 0, current = 0f, steps = 3))
        assertEquals(0.5f / 3, sequenceProgress(finished = 0, current = 0.5f, steps = 3))
        assertEquals(1.5f / 3, sequenceProgress(finished = 1, current = 0.5f, steps = 3))
        assertEquals(1f, sequenceProgress(finished = 2, current = 1f, steps = 3))
        assertEquals(0.5f, sequenceProgress(finished = 0, current = 0.5f, steps = 1))
    }

    @Test
    fun progressStaysBetweenNothingAndDone() {
        assertEquals(1f / 3, sequenceProgress(finished = 0, current = 1.4f, steps = 3))
        assertEquals(0f, sequenceProgress(finished = 0, current = -0.2f, steps = 3))
    }

    @Test
    fun chipsShowDoneNowAndNext() {
        val chips = stepChips(threeGames, index = 1, stepDone = false)
        assertEquals(listOf(ChipState.DONE, ChipState.NOW, ChipState.NEXT), chips.map { it.state })
        assertEquals(
            listOf("Game 1 of 3, Sums, done", "Game 2 of 3, Scan, now", "Game 3 of 3, Breathe, next"),
            chips.map { it.label },
        )
        assertEquals(ChipState.DONE, stepChips(threeGames, index = 1, stepDone = true)[1].state)
    }

    @Test
    fun pipNamesTheNextGame() {
        assertEquals("Nice one! Next up: slow breaths.", nextUpLine(MissionType.BREATHE))
        assertEquals("Nice one! Next up: kettle hunt.", nextUpLine(MissionType.SCAN))
    }

    @Test
    fun switchingToSumsReplacesOnlyThatGame() {
        assertEquals(
            listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2)),
            switchedToSums(threeGames, index = 1),
        )
    }
}
