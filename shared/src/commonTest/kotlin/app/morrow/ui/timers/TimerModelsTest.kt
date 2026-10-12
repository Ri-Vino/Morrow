package app.morrow.ui.timers

import app.morrow.timers.Timer
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

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
    fun saysPipIsReadingOnlyWhileHeReadsANote() {
        val tea = Timer(3, 4.minutes, noon + 10.minutes, null, "Take the tea bag out", noon + 6.minutes)

        assertEquals("Pip is reading your note", timerDoneModel(listOf(pasta, plain), london, speaking = true).reading)
        assertEquals("Pip is reading your notes", timerDoneModel(listOf(pasta, tea), london, speaking = true).reading)
        assertNull(timerDoneModel(listOf(pasta), london, speaking = false).reading)
        assertNull(timerDoneModel(listOf(plain), london, speaking = true).reading)
    }

    @Test
    fun timersEndingTogetherShareTheScreenAndItsButtons() {
        val model = timerDoneModel(listOf(pasta, plain), london)

        assertEquals(listOf(1L, 2L), model.timers.map { it.id })
        assertEquals("Stop all 2 timers", model.stopLabel)
        assertEquals("Ring all 2 again in a minute", model.addMinuteLabel)
    }
}
