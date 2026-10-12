package app.morrow.timers

/*
 * On iOS, AlarmKit holds alarms, their fixed dates and timers under one kind of id, 48 bits long, which
 * AlarmKitScheduler turns into UUIDs. Alarms use their own ids, below 2^32, and fixed dates add a slot from 1 to 8 above
 * them (app.morrow.alarms.fixedDateId), so everything else stays below 2^36. Timers take the top bit, 2^47.
 */

/** The bit that marks a timer in AlarmKit's ids. */
private const val TIMER_BIT = 1L shl 47

/** [timerId]'s id in AlarmKit. It never clashes with an alarm's or a fixed date's id. */
fun timerAlarmKitId(timerId: Long): Long {
    require(timerId in 1 until TIMER_BIT) { "timer ids must be from 1 to 2^47 - 1, was $timerId" }
    return TIMER_BIT or timerId
}

/** Whether an id AlarmKit holds is a timer's, from [timerAlarmKitId], rather than an alarm's or a fixed date's. */
fun isTimerAlarmKitId(id: Long): Boolean = (id and TIMER_BIT) != 0L

/** The timer whose AlarmKit id, from [timerAlarmKitId], this is. */
fun timerIdOf(alarmKitId: Long): Long = alarmKitId and (TIMER_BIT - 1)
