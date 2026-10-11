package app.morrow.ui.world

import app.morrow.ui.hhmm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Instant

/** The wall clock in [zone] at [now], like "07:40". */
fun cityClock(now: Instant, zone: TimeZone): String {
    val minute = cityMinute(now, zone)
    return hhmm(minute / 60, minute % 60)
}

/** Minutes since midnight on the wall clock in [zone] at [now]. */
fun cityMinute(now: Instant, zone: TimeZone): Int {
    val time = now.toLocalDateTime(zone).time
    return time.hour * 60 + time.minute
}

/** How a city's day and clock sit against the phone's, like "Today · −5 h" or "Tomorrow · +8 h". */
fun relativeDay(now: Instant, cityZone: TimeZone, phoneZone: TimeZone): String {
    val cityDate = now.toLocalDateTime(cityZone).date
    val phoneDate = now.toLocalDateTime(phoneZone).date
    val day = when {
        cityDate > phoneDate -> "Tomorrow"
        cityDate < phoneDate -> "Yesterday"
        else -> "Today"
    }
    val offsetMinutes = (now.offsetIn(cityZone).totalSeconds - now.offsetIn(phoneZone).totalSeconds) / 60
    return "$day · ${if (offsetMinutes == 0) "your time" else formatOffset(offsetMinutes)}"
}

private fun formatOffset(minutes: Int): String {
    val sign = if (minutes > 0) "+" else "−"
    val hours = abs(minutes) / 60
    return when (val rest = abs(minutes) % 60) {
        0 -> "$sign$hours h"
        30 -> "$sign$hours½ h"
        else -> "$sign$hours h $rest m"
    }
}
