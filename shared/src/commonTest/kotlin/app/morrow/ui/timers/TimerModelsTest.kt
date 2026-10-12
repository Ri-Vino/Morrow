package app.morrow.ui.timers

import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.IosPermissions
import app.morrow.timers.Timer
import app.morrow.timers.paused
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val london = TimeZone.of("Europe/London")
private val noon = LocalDateTime.parse("2026-10-12T12:32").toInstant(london)

class TimerDoneModelTest {

    private val pasta = Timer(1, 10.minutes, noon + 10.minutes, null, "Take the pasta off the hob", noon)
    private val plain = Timer(2, 25.minutes, noon + 10.minutes, null, "", noon - 15.minutes)

    @Test
    fun showsTheNoteAndWhenTheTimerEnded() {
        val model = timerDoneModel(listOf(pasta), london)

        assertEquals(listOf(DoneTimer(1, "Take the pasta off the hob", "10 min timer · ended at 12:42", hasNote = true)), model.timers)
        assertEquals("Stop the timer", model.stopLabel)
        assertEquals("Ring again in a minute", model.addMinuteLabel)
    }

    @Test
    fun aTimerWithoutANoteSaysWhichOneIsDone() {
        val done = timerDoneModel(listOf(plain), london).timers.single()

        assertEquals("Your 25 min timer is done", done.title)
        assertEquals("25 min timer · ended at 12:42", done.meta)
        assertEquals(false, done.hasNote)
    }

    @Test
    fun timersEndingTogetherShareTheScreenAndItsButtons() {
        val model = timerDoneModel(listOf(pasta, plain), london)

        assertEquals(listOf(1L, 2L), model.timers.map { it.id })
        assertEquals("Stop all 2 timers", model.stopLabel)
        assertEquals("Ring all 2 again in a minute", model.addMinuteLabel)
    }
}

class TimersModelTest {

    private val running = Timer(1, 10.minutes, noon + 10.minutes, null, "Pasta", noon)
    private val paused = Timer(2, 45.minutes, noon + 45.minutes, null, "", noon).paused(noon + 14.minutes + 30.seconds)
    private val ringing = Timer(3, 1.minutes, noon, null, "", noon - 1.minutes)
    private val waiting = Timer(4, 5.minutes, noon - 2.minutes, null, "Eggs", noon - 7.minutes)

    @Test
    fun eachTimerSaysWhetherItRunsWaitsOrRings() {
        val rows = timersModel(listOf(running, paused, ringing, waiting), ringing = setOf(3), waiting = setOf(4), london).rows

        assertEquals(
            listOf(
                TimerRow(1, "Pasta", "10 min · ends at 12:42", "Stop Pasta"),
                TimerRow(2, "45 min timer", "Paused · 30:30 left", "Stop 45 min timer"),
                TimerRow(3, "1 min timer", "Ringing now", "Stop 1 min timer"),
                TimerRow(4, "Eggs", "Ended at 12:30 · rings after your alarm", "Stop Eggs"),
            ),
            rows,
        )
    }

    @Test
    fun startAsksForWhatLetsTimersRingOnTime() {
        assertEquals(AlarmPermission.EXACT_ALARMS, timerPermission(AndroidPermissions))
        assertEquals(AlarmPermission.ALARMS, timerPermission(IosPermissions))
    }
}
