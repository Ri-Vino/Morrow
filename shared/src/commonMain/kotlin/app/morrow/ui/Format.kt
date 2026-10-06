package app.morrow.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Rounds up to the minute, so a wake-up 7 h 19 min 59 s away reads as the clock suggests: 7h 20m. */
internal fun formatDuration(duration: Duration): String {
    val totalMinutes = (duration.inWholeMilliseconds + 59_999) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "$minutes min"
}

internal fun formatDuration(minutes: Int): String = formatDuration(minutes.minutes)

internal fun hhmm(hour: Int, minute: Int) = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
