package app.morrow.ui.mission

import app.morrow.missions.BreatheNote
import app.morrow.missions.BreatheState
import app.morrow.missions.ScanState
import app.morrow.missions.ShakeState
import app.morrow.missions.Sum
import app.morrow.missions.SumsState
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionModelTest {

    private fun sums(solved: Int, count: Int = 3) = SumsState(Sum("2 + 2", 4), solved, count, answer = "", wrong = 0)

    @Test
    fun sumsCountTheOneYouAreOn() {
        assertEquals("1 of 3", sumsModel(sums(0)).count)
        assertEquals("3 of 3", sumsModel(sums(2)).count)
        assertEquals("3 of 3", sumsModel(sums(3)).count)
    }

    @Test
    fun pipWakesUpWithProgress() {
        assertEquals("Mmm… five more minutes?", pipLine(0f))
        assertEquals("Okay, okay. I’m stirring.", pipLine(1f / 3))
        assertEquals("Ooh, I can see the light.", pipLine(2f / 3))
        assertEquals("Nearly there, keep going!", pipLine(0.8f))
        assertEquals("We’re up! Well done.", pipLine(1f))
    }

    @Test
    fun breatheTalksYouThroughEachBreath() {
        val resting = BreatheState(breaths = 1, hold = 0f, holding = false, note = BreatheNote.TWO_MORE)
        assertEquals("1 of 3", breatheModel(resting).count)
        assertEquals("Press and hold", breatheLabel(resting))
        assertEquals("Lovely. Two more.", breatheHint(resting))
        assertEquals("Breathe in…", breatheLabel(resting.copy(holding = true, hold = 0.2f)))
        assertEquals("And out…", breatheLabel(resting.copy(holding = true, hold = 0.7f)))
        assertEquals("Beautiful.", breatheLabel(BreatheState(3, 0f, holding = false, note = null)))
    }

    @Test
    fun breatheCountsTheAlarmsBreaths() {
        val state = BreatheState(breaths = 1, hold = 0f, holding = false, note = BreatheNote.MORE, count = 5)
        assertEquals("Slow breaths", breatheModel(state).title)
        assertEquals("1 of 5", breatheModel(state).count)
        assertEquals("Lovely. Four more.", breatheHint(state))
    }

    @Test
    fun pipFollowsTheWholeSequence() {
        assertEquals("Mmm… five more minutes?", sumsModel(sums(0)).pipLine)
        assertEquals("Ooh, I can see the light.", sumsModel(sums(0), overall = 0.5f).pipLine)
        assertEquals("Nearly there, keep going!", shakeModel(ShakeState(shakes = 30, goal = 30), overall = 0.9f).pipLine)
    }

    @Test
    fun shakeCountsUpToTheGoal() {
        val model = shakeModel(ShakeState(shakes = 9, goal = 30))
        assertEquals("9 of 30", model.count)
        assertEquals("Okay, okay. I’m stirring.", model.pipLine)
    }

    @Test
    fun scanShowsWhatWentWrong() {
        val looking = ScanState("kettle", found = false, wrong = 0)
        assertEquals("Find the code", scanModel(looking, cameraAllowed = true).count)

        val wrong = looking.copy(wrong = 1)
        assertEquals("Try again", scanModel(wrong, cameraAllowed = true).count)
        assertEquals("Hmm, that’s not our code.", scanModel(wrong, cameraAllowed = true).pipLine)

        val off = scanModel(looking, cameraAllowed = false)
        assertEquals("Camera off", off.count)
        assertEquals("I can’t see a thing without the camera!", off.pipLine)

        assertEquals("Found it", scanModel(looking.copy(found = true), cameraAllowed = true).count)
    }
}
