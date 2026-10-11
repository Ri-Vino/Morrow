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

/** When this alarm next rings, by its own clock when it has one, on a phone in [phoneZone]. */
fun Alarm.nextRing(after: Instant, phoneZone: TimeZone): Instant = nextOccurrence(after, clockZone(phoneZone))

/** The zone whose clock this alarm keeps: its own, or [phoneZone] when it follows the phone or its zone isn't known here. */
fun Alarm.clockZone(phoneZone: TimeZone): TimeZone = ownZone() ?: phoneZone

/** The alarm's own zone, or null when it follows the phone. A zone id this device doesn't know counts as none. */
fun Alarm.ownZone(): TimeZone? = timeZone?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }

/**
 * The time this alarm's clock reads [hour]:[minute] closest to [at], whatever its repeat days: the moment it rang,
 * while it's ringing.
 */
fun Alarm.occurrenceNear(at: Instant, phoneZone: TimeZone): Instant {
    val zone = clockZone(phoneZone)
    val date = at.toLocalDateTime(zone).date
    return (-1..1)
        .map { LocalDateTime(date + DatePeriod(days = it), LocalTime(hour, minute)).toInstant(zone) }
        .minBy { (it - at).absoluteValue }
}

fun List<Alarm>.nextAlarm(after: Instant, phoneZone: TimeZone): NextAlarm? =
    filter { it.enabled }
        .map { NextAlarm(it, it.nextRing(after, phoneZone)) }
        .minByOrNull { it.at }
