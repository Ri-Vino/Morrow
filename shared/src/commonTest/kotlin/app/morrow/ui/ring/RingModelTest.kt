package app.morrow.ui.ring

import kotlin.test.assertNull
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.nextRing
import app.morrow.theme.Illustration
import app.morrow.ui.components.Icons
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val london = TimeZone.of("Europe/London")

class RingModelTest {

    private fun alarm(label: String, vararg games: MissionType = arrayOf(MissionType.SUMS)) =
        Alarm(1, 6, 5, label, emptySet(), games.map { GameStep(it) }, enabled = true)

    /** The model while [alarm] rings on its next occurrence after a Monday midnight in London. */
    private fun ringModel(alarm: Alarm) =
        app.morrow.ui.ring.ringModel(alarm, alarm.nextRing(LocalDateTime.parse("2026-10-05T00:00").toInstant(london), london), london)

    @Test
    fun anAlarmOnAnotherCitysClockShowsTheTimeHereAndThere() {
        val standUp = Alarm(1, 9, 0, "Stand-up", emptySet(), listOf(GameStep(MissionType.SUMS)), enabled = true, timeZone = "America/New_York", zoneLabel = "New York")
        val model = ringModel(standUp)
        assertEquals("14:00", model.time)
        assertEquals("Stand-up · 09:00 in New York", model.label)
        assertEquals(Icons.Globe, model.missionIcon)
        assertEquals("I’m up — start sums", model.startLabel)
    }

    @Test
    fun aDescriptionIsShownUnderTheLabel() {
        val model = ringModel(alarm("Gym", MissionType.SHAKE).copy(description = "  Gym bag is by the door "))
        assertEquals("Gym · Shake", model.label)
        assertEquals("Gym bag is by the door", model.description)
    }

    @Test
    fun noDescriptionOrABlankOneShowsNothing() {
        assertNull(ringModel(alarm("Gym")).description)
        assertNull(ringModel(alarm("Gym").copy(description = "   ")).description)
    }

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
    fun snoozeSaysHowLongAndHowManyAreLeft() {
        val alarm = alarm("Gym")
        assertEquals("Snooze 5 min", snoozeLabel(alarm, snoozed = 0))
        assertEquals("Snooze 10 min · 2 left", snoozeLabel(alarm.copy(snoozeMinutes = 10), snoozed = 1))
        assertEquals("Snooze 5 min · 1 left", snoozeLabel(alarm, snoozed = 2))
        // After the last snooze, or with snoozing off, only the games stop it.
        assertNull(snoozeLabel(alarm, snoozed = 3))
        assertNull(snoozeLabel(alarm.copy(snoozeMinutes = 0), snoozed = 0))
    }

    @Test
    fun eachAlarmSetsHowManyTimesItSnoozes() {
        val once = alarm("Gym").copy(snoozeLimit = 1)
        assertEquals("Snooze 5 min", snoozeLabel(once, snoozed = 0))
        assertNull(snoozeLabel(once, snoozed = 1))
        val often = alarm("Gym").copy(snoozeLimit = 10)
        assertEquals("Snooze 5 min · 4 left", snoozeLabel(often, snoozed = 6))
        assertNull(snoozeLabel(often, snoozed = 10))
    }

    @Test
    fun theTimeIsTheClocksNowNotTheAlarmsAsAfterASnooze() {
        val snoozedTwice = LocalDateTime.parse("2026-10-05T06:15").toInstant(london)
        assertEquals("06:15", app.morrow.ui.ring.ringModel(alarm("Gym"), snoozedTwice, london).time)
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
