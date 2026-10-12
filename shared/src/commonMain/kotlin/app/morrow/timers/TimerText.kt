package app.morrow.timers

import kotlin.time.Duration

/** A timer's length as the design writes it: "45 s", "10 min", "1 h" or "1 h 30 min", rounded to the minute. */
fun totalText(duration: Duration): String {
    val seconds = duration.inWholeSeconds
    if (seconds < 60) return "$seconds s"
    val minutes = (seconds + 30) / 60
    val hours = minutes / 60
    return when {
        hours == 0L -> "$minutes min"
        minutes % 60 == 0L -> "$hours h"
        else -> "$hours h ${minutes % 60} min"
    }
}

/** Time left as a countdown, "04:12" or "1:02:03". Rounded up, so it reads 00:00 only once the timer has ended. */
fun clockText(left: Duration): String {
    val seconds = (left.inWholeMilliseconds.coerceAtLeast(0) + 999) / 1000
    val hours = seconds / 3600
    val clock = "${pad(seconds / 60 % 60)}:${pad(seconds % 60)}"
    return if (hours > 0) "$hours:$clock" else clock
}

/** What to call a timer: its note, or "10 min timer" without one. */
val Timer.name: String get() = note.ifBlank { "${totalText(duration)} timer" }

private fun pad(value: Long) = value.toString().padStart(2, '0')
