package app.morrow.ui.timers

import app.morrow.timers.totalText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The digits typed into the timer setter. They shift in from the right into hh:mm:ss, so 1, 5, 0, 0 reads 00:15:00 and
 * ten minutes is 1, 0, 0, 0. A leading zero adds nothing, and a seventh digit is ignored.
 */
data class TimerEntry(val digits: String = "") {

    /** The entry after [typed] (a digit, or "00") is added on the right. It stays as it is if that would be too many digits. */
    fun type(typed: String): TimerEntry {
        val next = (digits + typed).trimStart('0')
        return if (next.length <= MAX_DIGITS) TimerEntry(next) else this
    }

    fun deleteLast(): TimerEntry = TimerEntry(digits.dropLast(1))

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
    }
}

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
