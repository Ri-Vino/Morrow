package app.morrow.ui.timers

import app.morrow.platform.AlarmPermission
import app.morrow.timers.Timer
import app.morrow.timers.clockText
import app.morrow.timers.name
import app.morrow.timers.totalText
import app.morrow.ui.world.cityClock
import kotlinx.datetime.TimeZone

/** One timer in the Timers tab's list. */
data class TimerRow(val id: Long, val title: String, val detail: String, val stopLabel: String)

data class TimersModel(val rows: List<TimerRow>)

/** The Timers tab's plain list, until the timer cards replace it: each timer, with whether it runs, waits or rings. */
fun timersModel(timers: List<Timer>, ringing: Set<Long>, waiting: Set<Long>, zone: TimeZone): TimersModel =
    TimersModel(
        timers.map { timer ->
            val endsAt = timer.endsAt
            val detail = when {
                timer.id in ringing -> "Ringing now"
                timer.id in waiting && endsAt != null -> "Ended at ${cityClock(endsAt, zone)} · rings after your alarm"
                endsAt != null -> "${totalText(timer.duration)} · ends at ${cityClock(endsAt, zone)}"
                else -> "Paused · ${clockText(timer.remaining ?: timer.duration)} left"
            }
            TimerRow(timer.id, timer.name, detail, stopLabel = "Stop ${timer.name}")
        },
    )

/** What Start asks for when the phone can't ring a timer on time: Android's exact alarms, or iOS's one permission. */
fun timerPermission(asked: List<AlarmPermission>): AlarmPermission =
    if (AlarmPermission.EXACT_ALARMS in asked) AlarmPermission.EXACT_ALARMS else AlarmPermission.ALARMS
