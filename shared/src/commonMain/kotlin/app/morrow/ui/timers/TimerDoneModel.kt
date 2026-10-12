package app.morrow.ui.timers

import app.morrow.timers.Timer
import app.morrow.timers.totalText
import app.morrow.ui.world.cityClock
import kotlinx.datetime.TimeZone

/** One timer on the Timer done screen. */
data class DoneTimer(
    val id: Long,
    /** The note, or "Your 10 min timer is done" without one. */
    val title: String,
    /** Like "10 min timer · ended at 12:42". */
    val meta: String,
    /** Whether there's a note for Pip to read out. */
    val hasNote: Boolean,
)

data class TimerDoneModel(
    val timers: List<DoneTimer>,
    /** What TalkBack says for Stop, which stops every timer on the screen. */
    val stopLabel: String,
    val addMinuteLabel: String,
)

/** The Timer done screen for [timers], which ended together or while the screen was up, in the order they ended. */
fun timerDoneModel(timers: List<Timer>, zone: TimeZone): TimerDoneModel {
    val many = timers.size > 1
    return TimerDoneModel(
        timers = timers.map { timer ->
            val total = totalText(timer.duration)
            val endedAt = timer.endsAt?.let { " · ended at ${cityClock(it, zone)}" }.orEmpty()
            DoneTimer(
                id = timer.id,
                title = timer.note.ifBlank { "Your $total timer is done" },
                meta = "$total timer$endedAt",
                hasNote = timer.note.isNotBlank(),
            )
        },
        stopLabel = if (many) "Stop all ${timers.size} timers" else "Stop the timer",
        addMinuteLabel = if (many) "Ring all ${timers.size} again in a minute" else "Ring again in a minute",
    )
}
