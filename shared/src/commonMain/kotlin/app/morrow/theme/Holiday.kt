package app.morrow.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The seasons Home and the ring screen dress up for, when Settings' "Seasonal looks" is on. */
enum class Holiday {
    /** The week up to and including 31 October. */
    HALLOWEEN,

    /** 31 October itself, when Pip's face paint goes all out. */
    HALLOWEEN_NIGHT,

    /** 1 to 30 December. */
    CHRISTMAS,

    /** New Year's Eve and New Year's Day. */
    NEW_YEAR;

    val halloween: Boolean get() = this == HALLOWEEN || this == HALLOWEEN_NIGHT

    /** Snow, lights and a tree stay up through New Year. */
    val christmas: Boolean get() = this == CHRISTMAS || this == NEW_YEAR

    companion object {
        /**
         * Dev tools only: a holiday every screen shows whatever the date, and whatever Settings says, until it's set back to
         * null. Snapshot state, so the screens showing it change as soon as it does.
         */
        var forced: Holiday? by mutableStateOf(null)

        /** The holiday at [now] in [zone], or null on an ordinary day or with seasonal looks turned off ([seasonal]). */
        fun at(now: Instant, zone: TimeZone, seasonal: Boolean): Holiday? =
            forced ?: if (seasonal) on(now.toLocalDateTime(zone).date) else null

        /** The holiday on [date] in the phone's own calendar, or null for an ordinary day. */
        fun on(date: LocalDate): Holiday? = when {
            date.month == Month.OCTOBER && date.day == 31 -> HALLOWEEN_NIGHT
            date.month == Month.OCTOBER && date.day >= HALLOWEEN_FROM -> HALLOWEEN
            date.month == Month.DECEMBER && date.day == 31 -> NEW_YEAR
            date.month == Month.JANUARY && date.day == 1 -> NEW_YEAR
            date.month == Month.DECEMBER -> CHRISTMAS
            else -> null
        }

        private const val HALLOWEEN_FROM = 24
    }
}
