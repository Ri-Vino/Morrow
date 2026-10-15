package app.morrow.ui.timers

import app.morrow.timers.totalText
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The setter's length as up to six digits, hh:mm:ss filled from the right, so 1, 5, 0, 0 is 00:15:00 and ten minutes is
 * 1, 0, 0, 0. It's what the setter keeps through rotation.
 */
data class TimerEntry(val digits: String = "") {

    private val padded: String get() = digits.padStart(MAX_DIGITS, '0')

    val hoursText: String get() = padded.substring(0, 2)
    val minutesText: String get() = padded.substring(2, 4)
    val secondsText: String get() = padded.substring(4, 6)

    /** Whether each part has been reached by the typing yet; until then it shows muted zeros. */
    val hoursTyped: Boolean get() = digits.length >= 5
    val minutesTyped: Boolean get() = digits.length >= 3
    val secondsTyped: Boolean get() = digits.isNotEmpty()

    /** The length the digits set. Minutes or seconds over 59 just add up, so 9, 0 is 90 seconds. */
    val duration: Duration get() = hoursText.toInt().hours + minutesText.toInt().minutes + secondsText.toInt().seconds

    /** Start needs some time to count down. */
    val canStart: Boolean get() = digits.isNotEmpty()

    /** What screen readers say for the digits. */
    val label: String get() = if (canStart) "Timer for ${totalText(duration)}" else "No time set yet"

    companion object {
        const val MAX_DIGITS = 6

        /** The entry [saved] digits make, ignoring anything that isn't a digit and any that wouldn't fit. */
        fun of(saved: String): TimerEntry = TimerEntry(saved.filter { it in '0'..'9' }.trimStart('0').take(MAX_DIGITS))

        /** The entry for [length], to the second, up to 99:59:59. */
        fun of(length: Duration): TimerEntry {
            val seconds = length.inWholeSeconds.coerceIn(0, MAX_SECONDS)
            val parts = listOf(seconds / 3600, seconds / 60 % 60, seconds % 60)
            return of(parts.joinToString("") { it.toString().padStart(2, '0') })
        }

        /** The entry typed as [hours], [minutes] and [seconds] in the Type it sheet, empty parts as 0; null past 99:59:59. */
        fun typed(hours: String, minutes: String, seconds: String): TimerEntry? {
            val parts = listOf(hours, minutes, seconds).map { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
            if (parts[1] > 59 || parts[2] > 59) return null
            return of(parts[0].hours + parts[1].minutes + parts[2].seconds)
        }

        private const val MAX_SECONDS = 99 * 3600L + 59 * 60 + 59
    }
}

/**
 * The timer dial's whole minutes once Pip has been dragged to [fraction] of a turn from the top, starting from
 * [minutes]. A turn is an hour. It takes whichever turn is nearest, so carrying on past the top winds on an hour, and
 * going back past it unwinds one; it stops at 0 and at [MAX_DIAL_MINUTES].
 */
fun windDial(minutes: Int, fraction: Float): Int {
    val hour = minutes - minutes.mod(60)
    val minute = (fraction * 60).roundToInt().mod(60)
    val nearest = listOf(hour + minute - 60, hour + minute, hour + minute + 60).minBy { abs(it - minutes) }
    return nearest.coerceIn(0, MAX_DIAL_MINUTES)
}

/** Twelve hours, wound on the dial a lap at a time; typing goes further. */
const val MAX_DIAL_MINUTES = 12 * 60

/** A quick chip's name and the digits it sets, written as the canvas has them: "1000" is ten minutes. */
data class QuickTimer(val name: String, val entry: TimerEntry)

val QuickTimers = listOf(
    QuickTimer("1 min", TimerEntry("100")),
    QuickTimer("3 min", TimerEntry("300")),
    QuickTimer("5 min", TimerEntry("500")),
    QuickTimer("10 min", TimerEntry("1000")),
    QuickTimer("25 min", TimerEntry("2500")),
    QuickTimer("1 hour", TimerEntry("10000")),
)

/** What the longest note is, as on Morning's "one small thing". */
const val MAX_NOTE_LENGTH = 60
