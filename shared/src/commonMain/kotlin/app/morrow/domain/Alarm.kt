package app.morrow.domain

import kotlinx.datetime.DayOfWeek

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    /** Empty means the alarm rings once. */
    val days: Set<DayOfWeek>,
    val mission: MissionType,
    val enabled: Boolean,
) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
    }

    val repeats: Boolean get() = days.isNotEmpty()
}
