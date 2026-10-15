package app.morrow.ui.timers

import androidx.compose.runtime.saveable.SaverScope
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.IosPermissions
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.mix
import app.morrow.timers.Timer
import app.morrow.timers.paused
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val london = TimeZone.of("Europe/London")
private val noon = LocalDateTime.parse("2026-10-12T12:32").toInstant(london)

class TimerSkyTest {

    private fun assertNear(expected: Float, actual: Float) = assertEquals(expected, actual, absoluteTolerance = 0.001f)

    @Test
    fun theSunStartsAtMiddayHighOnTheLeft() {
        val sky = timerSky(0f)

        assertNear(720f, sky.minuteOfDay)
        assertEquals(SkyCurve.at(720f), sky.sky)
        assertNear(0f, sky.starAlpha)
        assertFalse(sky.sleepy)
    }

    @Test
    fun itHalfwayIsMidAfternoonAndTheSunIsDownTheSlope() {
        val sky = timerSky(0.5f)

        assertNear(940f, sky.minuteOfDay)
        assertEquals(SkyCurve.at(940f), sky.sky)
        assertNear(0f, sky.starAlpha)
    }

    @Test
    fun theSunSinksToDuskAsTheTimeRunsOut() {
        val sky = timerSky(1f)

        assertNear(1160f, sky.minuteOfDay)
        assertEquals(SkyCurve.at(19 * 60 + 20f), sky.sky)
        assertNear(0.75f, sky.starAlpha)
        assertTrue(sky.sleepy)
    }

    @Test
    fun starsComeOutOverTheLastQuarter() {
        assertNear(0f, timerSky(0.75f).starAlpha)
        assertNear(0.3f, timerSky(0.85f).starAlpha)
    }

    @Test
    fun pipDozesOffFromEightyFivePercent() {
        assertFalse(timerSky(0.84f).sleepy)
        assertTrue(timerSky(0.85f).sleepy)
    }

    @Test
    fun theHillsAreTheSkyDarkened() {
        val sky = timerSky(0.5f)

        assertEquals(mix(sky.sky, Illustration.ground, 0.35f), sky.hillBack)
        assertEquals(mix(sky.sky, Illustration.ground, 0.55f), sky.hillFront)
    }

    @Test
    fun progressOutsideZeroToOneIsHeldAtTheEnds() {
        assertEquals(timerSky(1f), timerSky(3f))
        assertEquals(timerSky(0f), timerSky(-1f))
    }
}

class TimersModelTest {

    private val pasta = Timer(1, 10.minutes, noon + 10.minutes, null, "Pasta", noon)
    private val plain = Timer(2, 25.minutes, noon + 25.minutes, null, "", noon)
    private val paused = Timer(3, 45.minutes, noon + 45.minutes, null, "", noon).paused(noon + 14.minutes + 30.seconds)

    private fun model(
        timers: List<Timer>,
        now: Instant = noon,
        ringing: Set<Long> = emptySet(),
        waiting: Set<Long> = emptySet(),
        draft: TimerDraft = TimerDraft(),
    ) = timersModel(timers, ringing, waiting, now, london, draft)

    private fun card(timer: Timer, now: Instant = noon, ringing: Set<Long> = emptySet(), waiting: Set<Long> = emptySet()) =
        model(listOf(timer), now, ringing, waiting).cards.single()

    @Test
    fun aRunningTimerCountsDownWithItsNoteAndTotal() {
        val card = card(pasta, now = noon + 2.minutes + 30.seconds)

        assertEquals(TimerState.RUNNING, card.state)
        assertEquals("07:30", card.time)
        assertEquals("Pasta · 10 min", card.detail)
        assertTrue(card.canToggle)
        assertFalse(card.longClock)
    }

    @Test
    fun theLabelsNameTheTimer() {
        val card = card(pasta)

        assertEquals("Pause Pasta", card.toggleLabel)
        assertEquals("Add a minute to Pasta", card.addLabel)
        assertEquals("Delete Pasta", card.removeLabel)
    }

    @Test
    fun aTimerWithoutANoteIsNamedByItsLength() {
        val card = card(plain)

        assertEquals("25 min", card.detail)
        assertEquals("Pause 25 min timer", card.toggleLabel)
        assertEquals("Add a minute to 25 min timer", card.addLabel)
        assertEquals("Delete 25 min timer", card.removeLabel)
    }

    @Test
    fun aPausedTimerHoldsWhatsLeftAndOffersToResume() {
        val card = card(paused, now = noon + 20.minutes)

        assertEquals(TimerState.PAUSED, card.state)
        assertEquals("30:30", card.time)
        assertEquals("Paused · 45 min", card.detail)
        assertEquals("Resume 45 min timer", card.toggleLabel)
        assertTrue(card.canToggle)
    }

    @Test
    fun theCountdownRoundsUpSoItOnlyReadsZeroOnceItsOver() {
        assertEquals("00:01", card(pasta, now = noon + 10.minutes - 400.milliseconds).time)
        assertEquals("10:00", card(pasta, now = noon).time)
    }

    @Test
    fun hoursShowInASmallerClock() {
        val long = Timer(4, 90.minutes, noon + 90.minutes, null, "", noon)

        val card = card(long, now = noon + 27.minutes + 57.seconds)

        assertEquals("1:02:03", card.time)
        assertTrue(card.longClock)
        assertEquals("1 h 30 min", card.detail)
        assertFalse(card(pasta).longClock)
    }

    @Test
    fun theWindowFollowsHowFarTheTimerHasRun() {
        val card = card(pasta, now = noon + 5.minutes)

        assertEquals(timerSky(0.5f), card.sky)
        assertEquals(timerSky(0f), card(pasta).sky)
    }

    @Test
    fun aPausedTimersWindowStaysWhereItStopped() {
        // 14 minutes 30 seconds of 45 have run.
        assertEquals(timerSky((14.5 / 45).toFloat()), card(paused, now = noon + 3.minutes).sky)
        assertEquals(timerSky((14.5 / 45).toFloat()), card(paused, now = noon + 3.hours).sky)
    }

    @Test
    fun aRingingTimerSaysSoRatherThanCountingDown() {
        val card = card(Timer(5, 1.minutes, noon, null, "", noon - 1.minutes), now = noon + 5.seconds, ringing = setOf(5))

        assertEquals(TimerState.RINGING, card.state)
        assertEquals(TIME_IS_UP, card.time)
        assertEquals("Ringing now · 1 min", card.detail)
        assertFalse(card.canToggle)
        assertEquals(timerSky(1f), card.sky)
    }

    @Test
    fun aTimerWaitingBehindAnAlarmSaysItRingsAfterwards() {
        val eggs = Timer(6, 5.minutes, noon - 2.minutes, null, "Eggs", noon - 7.minutes)

        val card = card(eggs, ringing = emptySet(), waiting = setOf(6))

        assertEquals(TimerState.WAITING, card.state)
        assertEquals(TIME_IS_UP, card.time)
        assertEquals("Rings after your alarm · Eggs · 5 min", card.detail)
        assertFalse(card.canToggle)
    }

    @Test
    fun aTimerThatEndedIsNeverShownAsZeroCounting() {
        val ended = Timer(7, 5.minutes, noon, null, "", noon - 5.minutes)

        val card = card(ended, now = noon + 1.seconds)

        assertEquals(TimerState.ENDED, card.state)
        assertEquals(TIME_IS_UP, card.time)
        assertEquals("Ended at 12:32 · 5 min", card.detail)
        assertFalse(card.canToggle)
    }

    @Test
    fun anEndedTimerCanStillBeGivenAMinuteOrDeleted() {
        val card = card(Timer(5, 1.minutes, noon, null, "Tea", noon - 1.minutes), ringing = setOf(5))

        assertEquals("Add a minute to Tea", card.addLabel)
        assertEquals("Delete Tea", card.removeLabel)
    }

    @Test
    fun cardsFollowTheOrderOfTheTimers() {
        assertEquals(listOf(1L, 2L, 3L), model(listOf(pasta, plain, paused)).cards.map { it.id })
    }

    @Test
    fun withNoTimersTheSetterShowsAloneAndCantBeCancelled() {
        val model = model(emptyList(), draft = TimerDraft(TimerEntry("1000"), "Tea"))

        assertEquals(SetterModel(TimerEntry("1000"), "Tea", canCancel = false), model.setter)
        assertEquals(emptyList(), model.cards)
        assertFalse(model.showNewTimer)
        assertEquals("Pip reads your note when one ends", model.sub)
    }

    @Test
    fun withTimersRunningTheTabOffersNewTimerInsteadOfTheSetter() {
        val model = model(listOf(pasta))

        assertNull(model.setter)
        assertTrue(model.showNewTimer)
        assertEquals(1, model.cards.size)
    }

    @Test
    fun newTimerOpensTheSetterAboveTheCardsAndItCanBeCancelled() {
        val model = model(listOf(pasta), draft = TimerDraft(TimerEntry("500"), adding = true))

        assertEquals(SetterModel(TimerEntry("500"), "", canCancel = true), assertNotNull(model.setter))
        assertEquals(1, model.cards.size)
        assertFalse(model.showNewTimer)
    }

    @Test
    fun theSubtitleCountsWhatsRunningPausedAndEnded() {
        val ended = Timer(7, 5.minutes, noon, null, "", noon - 5.minutes)

        assertEquals("2 running", model(listOf(pasta, plain)).sub)
        assertEquals("2 running · 1 paused", model(listOf(pasta, plain, paused)).sub)
        assertEquals("1 paused", model(listOf(paused)).sub)
        assertEquals("1 running · 1 ended", model(listOf(pasta, ended), now = noon + 1.seconds).sub)
        assertEquals("1 ended", model(listOf(ended), now = noon + 1.seconds, ringing = setOf(7)).sub)
    }

    @Test
    fun startAsksForWhatLetsTimersRingOnTime() {
        assertEquals(AlarmPermission.EXACT_ALARMS, timerPermission(AndroidPermissions))
        assertEquals(AlarmPermission.ALARMS, timerPermission(IosPermissions))
    }

    @Test
    fun theDraftSurvivesBeingSaved() {
        val draft = TimerDraft(TimerEntry("1530"), "Take the pasta off", adding = true)

        val saved = with(TimerDraft.Saver) { SaverScope { true }.save(draft) }

        assertEquals(draft, TimerDraft.Saver.restore(assertNotNull(saved)))
    }
}
