package app.morrow.alarms

import app.morrow.domain.Alarm
import app.morrow.domain.nextOccurrence
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/*
 * iOS's AlarmKit repeats an alarm by the phone's clock, so an alarm on another city's clock goes over as single dates
 * instead: its next week of rings, each under an id of its own. The app tops them up whenever it opens.
 */

/** How far ahead the dates of an alarm on another city's clock are handed over. */
val FixedDatesAhead = 7.days

/** Ids for this many dates per alarm. A week can hold eight rings when the clocks change in between. */
const val FIXED_DATE_SLOTS = 8

/** When [alarm] rings from [first], its next ring, until a week later, by [zone]'s clock; only [first] for a one-off. */
fun Alarm.fixedDates(first: Instant, zone: TimeZone): List<Instant> {
    if (!repeats) return listOf(first)
    val dates = mutableListOf(first)
    while (dates.size < FIXED_DATE_SLOTS) {
        val next = nextOccurrence(dates.last(), zone)
        if (next >= first + FixedDatesAhead) break
        dates += next
    }
    return dates
}

/** The id for [alarmId]'s date in [slot], from 1 to [FIXED_DATE_SLOTS]. It never clashes with an alarm's own id. */
fun fixedDateId(alarmId: Long, slot: Int): Long {
    require(slot in 1..FIXED_DATE_SLOTS) { "slot must be 1..$FIXED_DATE_SLOTS, was $slot" }
    return (slot.toLong() shl 32) or alarmId
}

/** The alarm that a scheduled id, its own or one of its dates', belongs to. */
fun alarmIdOf(scheduledId: Long): Long = scheduledId and 0xFFFFFFFFL
