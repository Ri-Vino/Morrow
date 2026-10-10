package app.morrow.missions

import app.morrow.domain.Difficulty
import app.morrow.platform.Acceleration
import app.morrow.platform.MotionSensor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class SumsMissionTest {

    private fun SumsMission.answer(value: Int) {
        value.toString().forEach { type(it.digitToInt()) }
        check()
    }

    @Test
    fun sumsAreTheThreeKindsWithPositiveAnswers() {
        val random = Random(7)
        val sums = List(300) { randomSum(random) }
        assertTrue(sums.all { it.answer > 0 })
        for (sign in listOf("+", "−", "×")) assertTrue(sums.any { sign in it.question }, "no $sign sums")
    }

    @Test
    fun gentleSumsAreSmallAdditionsAndSubtractions() {
        val random = Random(3)
        val sums = List(300) { randomSum(random, Difficulty.GENTLE) }
        assertTrue(sums.all { it.answer in 1..29 })
        assertTrue(sums.none { "×" in it.question })
        assertTrue(sums.all { sum -> sum.question.split(" ").let { it[0].toInt() < 30 && it[2].toInt() < 30 } })
    }

    @Test
    fun toughSumsUseBiggerNumbersAndHarderTimesTables() {
        val random = Random(5)
        val sums = List(300) { randomSum(random, Difficulty.TOUGH) }
        assertTrue(sums.all { it.answer > 0 })
        val (times, others) = sums.partition { "×" in it.question }
        assertTrue(times.isNotEmpty() && times.all { sum -> sum.question.split(" ").let { it[0].toInt() >= 6 && it[2].toInt() >= 6 } })
        assertTrue(others.isNotEmpty() && others.all { it.question.split(" ")[0].toInt() >= 120 })
    }

    @Test
    fun theDifficultyCarriesOnToEverySum() {
        val mission = SumsMission(count = 5, Random(2), Difficulty.GENTLE)
        repeat(4) {
            assertTrue(mission.state.value.sum.answer < 30)
            mission.answer(mission.state.value.sum.answer)
        }
        assertTrue(mission.state.value.sum.answer < 30)
    }

    @Test
    fun rightAnswersCountUntilDone() {
        val mission = SumsMission(count = 2, Random(1))
        mission.answer(mission.state.value.sum.answer)
        assertEquals(0.5f, mission.state.value.progress)
        mission.answer(mission.state.value.sum.answer)
        assertTrue(mission.state.value.done)
    }

    @Test
    fun wrongAnswerClearsAndCountsAsWrong() {
        val mission = SumsMission(count = 3, Random(1))
        val sum = mission.state.value.sum
        mission.answer(sum.answer + 1)
        val state = mission.state.value
        assertEquals(sum, state.sum)
        assertEquals("", state.answer)
        assertEquals(1, state.wrong)
        assertEquals(0f, state.progress)
    }

    @Test
    fun deleteRemovesTheLastDigitAndAnswersStayShort() {
        val mission = SumsMission(count = 3)
        repeat(6) { mission.type(9) }
        assertEquals("9999", mission.state.value.answer)
        mission.delete()
        assertEquals("999", mission.state.value.answer)
    }

    @Test
    fun checkingNothingDoesNothing() {
        val mission = SumsMission(count = 3)
        mission.check()
        assertEquals(0, mission.state.value.wrong)
    }

    @Test
    fun inputIsIgnoredOnceDone() {
        val mission = SumsMission(count = 1, Random(3))
        mission.answer(mission.state.value.sum.answer)
        mission.type(4)
        mission.answer(0)
        assertEquals("", mission.state.value.answer)
        assertEquals(0, mission.state.value.wrong)
    }
}

class BreatheMissionTest {

    private val time = TestTimeSource()
    private val mission = BreatheMission(time = time)

    private fun breathe() {
        mission.press()
        time += BreatheMission.HOLD
        mission.tick()
    }

    @Test
    fun holdingFillsTheBreath() {
        mission.press()
        time += 1.seconds
        mission.tick()
        assertEquals(0.25f, mission.state.value.hold)
        assertTrue(mission.state.value.holding)
        assertEquals(0.25f / 3, mission.state.value.progress)
    }

    @Test
    fun lettingGoEarlyStartsTheBreathAgain() {
        mission.press()
        time += 3.seconds
        mission.release()
        val state = mission.state.value
        assertEquals(0, state.breaths)
        assertEquals(0f, state.hold)
        assertFalse(state.holding)
        assertEquals(BreatheNote.TOO_QUICK, state.note)
    }

    @Test
    fun lettingGoAfterAFullBreathKeepsIt() {
        mission.press()
        time += 4.seconds + 100.milliseconds
        mission.release()
        assertEquals(1, mission.state.value.breaths)
        assertEquals(BreatheNote.TWO_MORE, mission.state.value.note)
    }

    @Test
    fun threeBreathsFinish() {
        breathe()
        breathe()
        assertEquals(BreatheNote.ONE_MORE, mission.state.value.note)
        breathe()
        assertTrue(mission.state.value.done)
        mission.press()
        assertFalse(mission.state.value.holding)
    }

    @Test
    fun theAlarmSetsHowManyBreaths() {
        val long = BreatheMission(5, time)
        fun breatheLong() {
            long.press()
            time += BreatheMission.HOLD
            long.tick()
        }
        breatheLong()
        assertEquals(BreatheNote.MORE, long.state.value.note)
        assertEquals(4, long.state.value.left)
        repeat(3) { breatheLong() }
        assertFalse(long.state.value.done)
        breatheLong()
        assertTrue(long.state.value.done)

        val one = BreatheMission(1, time)
        one.press()
        time += BreatheMission.HOLD
        one.tick()
        assertTrue(one.state.value.done)
    }
}

class ShakeMissionTest {

    private fun jolt(atMillis: Long) = Acceleration(25f, 0f, 9.8f, atMillis * 1_000_000)
    private fun still(atMillis: Long) = Acceleration(0f, 9.8f, 0f, atMillis * 1_000_000)

    private class FakeSensor(private val readings: List<Acceleration>) : MotionSensor {
        var listening = false
        override val available = true
        override fun accelerations(): Flow<Acceleration> = callbackFlow {
            listening = true
            readings.forEach { send(it) }
            awaitClose { listening = false }
        }
    }

    @Test
    fun detectorIgnoresGravityAndCountsEachJoltOnce() {
        val detector = ShakeDetector()
        assertFalse(detector.isShake(still(0)))
        assertTrue(detector.isShake(jolt(100)))
        assertFalse(detector.isShake(jolt(150)))
        assertTrue(detector.isShake(jolt(400)))
    }

    @Test
    fun aHigherThresholdNeedsAFirmerShake() {
        val firm = ShakeDetector(threshold = Difficulty.TOUGH.shakeForce)
        val soft = ShakeDetector(threshold = Difficulty.GENTLE.shakeForce)
        // About 2.1 g: enough for Gentle and Normal, not for Tough.
        val medium = Acceleration(20.6f, 0f, 0f, 0)
        assertTrue(soft.isShake(medium))
        assertTrue(ShakeDetector(threshold = Difficulty.NORMAL.shakeForce).isShake(medium))
        assertFalse(firm.isShake(medium))
    }

    @Test
    fun countsShakesAndStopsTheSensorAtTheGoal() = runTest {
        val readings = (0 until 10).flatMap { listOf(jolt(it * 300L), still(it * 300L + 100)) }
        val sensor = FakeSensor(readings)
        val mission = ShakeMission(goal = 3)
        mission.listen(sensor)
        assertTrue(mission.state.value.done)
        assertEquals(3, mission.state.value.shakes)
        assertFalse(sensor.listening)
    }
}

class ScanMissionTest {

    @Test
    fun theSavedCodeFinishes() {
        val mission = ScanMission("kettle")
        mission.scanned("kettle")
        assertTrue(mission.state.value.done)
    }

    @Test
    fun anotherCodeIsWrong() {
        val mission = ScanMission("kettle")
        mission.scanned("cereal box")
        mission.scanned("cereal box")
        assertEquals(2, mission.state.value.wrong)
        assertFalse(mission.state.value.done)
    }

    @Test
    fun withNothingSavedTheFirstCodeIsRemembered() {
        val mission = ScanMission(null)
        assertNull(mission.state.value.savedCode)
        mission.scanned("kettle")
        assertEquals("kettle", mission.state.value.savedCode)
        assertTrue(mission.state.value.done)
    }
}
