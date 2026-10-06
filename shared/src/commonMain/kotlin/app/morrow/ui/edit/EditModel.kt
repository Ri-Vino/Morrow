package app.morrow.ui.edit

import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.ui.formatDuration
import app.morrow.ui.hhmm
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

private const val MINUTES_PER_DAY = 24 * 60

/** The dial moves in steps this size. */
const val DIAL_STEP_MINUTES = 5

data class EditModel(
    val title: String,
    val canDelete: Boolean,
    val time: String,
    val daypart: String,
    val untilText: String,
    val missionDescription: String,
)

fun editModel(draft: Alarm, nowMinute: Int, sumsCount: Int = 3, shakeGoal: Int = 30): EditModel {
    val alarmMinute = draft.hour * 60 + draft.minute
    val until = (alarmMinute - nowMinute).mod(MINUTES_PER_DAY).takeIf { it != 0 } ?: MINUTES_PER_DAY
    val night = nowMinute >= 20 * 60 || nowMinute < 4 * 60
    return EditModel(
        title = if (draft.id == 0L) "New alarm" else "Edit alarm",
        canDelete = draft.id != 0L,
        time = hhmm(draft.hour, draft.minute),
        daypart = daypart(alarmMinute),
        untilText = if (night) "Sleep now and that’s ${formatDuration(until)} of rest" else "That’s ${formatDuration(until)} from now",
        missionDescription = when (draft.mission) {
            MissionType.SUMS -> "Solve $sumsCount quick sum${if (sumsCount == 1) "" else "s"}. Best if you sleep through anything."
            MissionType.BREATHE -> "Hold for three slow breaths. The gentlest way up."
            MissionType.SHAKE -> "Shake your phone $shakeGoal times to get the blood moving."
            MissionType.SCAN -> "Walk to the kettle and scan its code. Tea’s on."
        },
    )
}

fun daypart(minuteOfDay: Int): String = when {
    minuteOfDay < 5 * 60 -> "Deep night"
    minuteOfDay < 6 * 60 -> "Before dawn"
    minuteOfDay < 7 * 60 -> "Dawn"
    minuteOfDay < 11 * 60 -> "Morning"
    minuteOfDay < 14 * 60 -> "Midday"
    minuteOfDay < 17 * 60 -> "Afternoon"
    minuteOfDay < 18 * 60 + 30 -> "Golden hour"
    minuteOfDay < 20 * 60 -> "Dusk"
    else -> "Night"
}

/**
 * The time at a point on the dial, given as an offset from its centre with y pointing down.
 * Midnight is at the top and time runs clockwise, snapped to [DIAL_STEP_MINUTES].
 */
fun dialMinute(dx: Float, dy: Float): Int {
    var angle = atan2(dx.toDouble(), -dy.toDouble())
    if (angle < 0) angle += 2 * PI
    val steps = (angle / (2 * PI) * MINUTES_PER_DAY / DIAL_STEP_MINUTES).roundToInt()
    return (steps * DIAL_STEP_MINUTES) % MINUTES_PER_DAY
}

fun Alarm.nudged(byMinutes: Int): Alarm {
    val minute = (hour * 60 + minute + byMinutes).mod(MINUTES_PER_DAY)
    return copy(hour = minute / 60, minute = minute % 60)
}

fun Alarm.at(minuteOfDay: Int): Alarm = copy(hour = minuteOfDay / 60, minute = minuteOfDay % 60)
