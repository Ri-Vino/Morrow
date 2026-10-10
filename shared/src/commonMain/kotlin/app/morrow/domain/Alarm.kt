package app.morrow.domain

import kotlinx.datetime.DayOfWeek

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    /** Empty means the alarm rings once. */
    val days: Set<DayOfWeek>,
    /** The wake-up games in the order they're played: never empty, and each game at most once. */
    val games: List<GameStep>,
    val enabled: Boolean,
) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
        require(games.isNotEmpty()) { "an alarm needs at least one game" }
        require(games.distinctBy { it.type }.size == games.size) { "each game can be played only once, was $games" }
    }

    val repeats: Boolean get() = days.isNotEmpty()
}
