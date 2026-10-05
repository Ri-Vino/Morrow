package app.morrow.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class NextAlarm(val alarm: Alarm, val at: Instant)

/**
 * The first time strictly after [after] that this alarm's wall-clock time comes round in [timeZone].
 *
 * A time that falls in a daylight-saving gap rings once the clocks have jumped (01:30 becomes 02:30).
 * A time that happens twice when the clocks go back rings only the first time.
 */
fun Alarm.nextOccurrence(after: Instant, timeZone: TimeZone): Instant {
    val today = after.toLocalDateTime(timeZone).date
    val time = LocalTime(hour, minute)
    // Today plus a full week covers a weekly alarm whose time already passed today.
    for (offset in 0..7) {
        val date = today + DatePeriod(days = offset)
        if (repeats && date.dayOfWeek !in days) continue
        val candidate = LocalDateTime(date, time).toInstant(timeZone)
        if (candidate > after) return candidate
    }
    error("No occurrence within a week for $this")
}

fun List<Alarm>.nextAlarm(after: Instant, timeZone: TimeZone): NextAlarm? =
    filter { it.enabled }
        .map { NextAlarm(it, it.nextOccurrence(after, timeZone)) }
        .minByOrNull { it.at }
