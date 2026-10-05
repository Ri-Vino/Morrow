package app.morrow.domain

import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

object SleepMath {
    val cycleLength: Duration = 90.minutes

    fun untilNextAlarm(alarms: List<Alarm>, now: Instant, timeZone: TimeZone): Duration? =
        alarms.nextAlarm(now, timeZone)?.let { it.at - now }

    fun fullCycles(sleep: Duration): Int = (sleep.inWholeMinutes / cycleLength.inWholeMinutes).toInt()
}
