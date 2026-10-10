package app.morrow.ui.ring

import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.theme.Illustration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingModelTest {

    private fun alarm(label: String, vararg games: MissionType = arrayOf(MissionType.SUMS)) =
        Alarm(1, 6, 5, label, emptySet(), games.map { GameStep(it) }, enabled = true)

    @Test
    fun showsTimeLabelAndGames() {
        val model = ringModel(alarm("Work days", MissionType.BREATHE))
        assertEquals("06:05", model.time)
        assertEquals("Work days · Breathe", model.label)
        assertEquals("I’m up — start breathe", model.startLabel)
    }

    @Test
    fun severalGamesAreNamedInOrderAndStartWithTheFirst() {
        val model = ringModel(alarm("Work days", MissionType.SHAKE, MissionType.SUMS, MissionType.SCAN))
        assertEquals("Work days · Shake, Sums and Scan", model.label)
        assertEquals("I’m up — start shake", model.startLabel)
        assertEquals("Work days · Sums and Breathe", ringModel(alarm("Work days", MissionType.SUMS, MissionType.BREATHE)).label)
    }

    @Test
    fun blankLabelShowsOnlyTheGames() {
        assertEquals("Sums", ringModel(alarm("  ")).label)
    }

    @Test
    fun startsAtNightWithPipAsleepBelowTheHills() {
        val frame = sunriseFrame(0f)
        assertEquals(Illustration.sunriseBands.map { it.first }, frame.bands)
        assertEquals(Illustration.sunriseHill.first, frame.hill)
        assertEquals(0f, frame.rise)
        assertEquals(1f, frame.eyesShut)
        assertEquals(0f, frame.yawn)
        assertEquals(0f, frame.smile)
        assertEquals(1f, frame.starAlpha)
        assertEquals(listOf(0f, 0f, 0f), frame.textIn)
    }

    @Test
    fun skyWarmsToDawnOverSevenSeconds() {
        val frame = sunriseFrame(7f)
        assertEquals(Illustration.sunriseBands.map { it.second }, frame.bands)
        assertEquals(Illustration.sunriseHill.second, frame.hill)
        assertEquals(1f, frame.rise)
        assertEquals(0f, frame.starAlpha)
        assertTrue(sunriseFrame(3.5f).bands.zip(frame.bands).all { (mid, end) -> mid != end })
    }

    @Test
    fun yawnsFromTwoPointThreeSecondsUntilItWakes() {
        assertEquals(0f, sunriseFrame(2.3f).yawn)
        assertEquals(1f, sunriseFrame(2.7f).yawn)
        assertEquals(1f, sunriseFrame(3.6f).yawn)
        assertEquals(0f, sunriseFrame(3.9f).yawn)
    }

    @Test
    fun opensItsEyesAtThreePointSixAndSmiles() {
        val asleep = sunriseFrame(3.6f)
        assertEquals(1f, asleep.eyesShut)
        assertEquals(0f, asleep.eyesOpen)
        val awake = sunriseFrame(4.2f)
        assertEquals(0f, awake.eyesShut)
        assertEquals(1f, awake.eyesOpen)
        assertEquals(1f, awake.smile)
    }

    @Test
    fun finishedSunriseIsTheLastFrame() {
        assertEquals(sunriseFrame(60f), sunriseFrame(SUNRISE_DONE))
    }

    @Test
    fun sunriseHasFinishedBySunriseLength() {
        assertEquals(sunriseFrame(SUNRISE_DONE), sunriseFrame(SUNRISE_LENGTH))
    }
}
