package app.morrow.ui.edit

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

enum class TimePart { HOURS, MINUTES }

/**
 * The Set the time sheet's typing on the 24-hour clock: hours, then minutes, each up to two digits. It opens on the
 * alarm's time with the hours picked, and the first digit typed into a picked part replaces what it showed. Once the
 * hours can't take another digit (two typed, or a first one of 3 or more), typing moves on to the minutes. Keys that
 * couldn't make a real time are off, so what's typed is always a time.
 */
data class TimeEntry(
    val hours: String,
    val minutes: String,
    val part: TimePart = TimePart.HOURS,
    /** Whether [part] still shows what it had when it was picked, so the next digit replaces it. */
    val fresh: Boolean = true,
) {
    /** The minute of the day typed, or null while the hours are empty. */
    val minuteOfDay: Int? get() = typedTime(hours, minutes)

    /** Whether typing [digit] makes sense: after a first hour digit of 2, only 0 to 3 do. */
    fun canType(digit: Int): Boolean = part == TimePart.MINUTES || fresh || hours != "2" || digit <= 3

    fun type(digit: Int): TimeEntry {
        if (!canType(digit)) return this
        return when (part) {
            TimePart.HOURS -> {
                val typed = (if (fresh) "" else hours) + digit
                if (typed.length == 2 || digit >= 3) {
                    copy(hours = typed.padStart(2, '0'), part = TimePart.MINUTES, fresh = true)
                } else {
                    copy(hours = typed, fresh = false)
                }
            }
            TimePart.MINUTES -> {
                // Two minute digits are a whole time already, so a third starts the minutes again.
                val typed = (if (fresh || minutes.length == 2) "" else minutes) + digit
                copy(minutes = if (typed.length == 1 && digit >= 6) "0$digit" else typed, fresh = false)
            }
        }
    }

    /** Takes off the last digit typed. A part that's just been picked empties, and empty minutes go back to the hours. */
    fun delete(): TimeEntry {
        val current = if (part == TimePart.HOURS) hours else minutes
        return when {
            fresh || current.isNotEmpty() -> {
                val left = if (fresh) "" else current.dropLast(1)
                if (part == TimePart.HOURS) copy(hours = left, fresh = false) else copy(minutes = left, fresh = false)
            }
            part == TimePart.MINUTES -> copy(hours = hours.dropLast(1), part = TimePart.HOURS, fresh = false)
            else -> this
        }
    }

    fun pick(part: TimePart): TimeEntry = copy(part = part, fresh = true)

    companion object {
        fun at(minuteOfDay: Int) = TimeEntry(two(minuteOfDay / 60), two(minuteOfDay % 60))

        val Saver: Saver<TimeEntry, Any> = listSaver(
            save = { listOf(it.hours, it.minutes, it.part.name, it.fresh) },
            restore = { TimeEntry(it[0] as String, it[1] as String, TimePart.valueOf(it[2] as String), it[3] as Boolean) },
        )

        private fun two(value: Int) = value.toString().padStart(2, '0')
    }
}
