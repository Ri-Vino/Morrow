package app.morrow.domain

import kotlinx.datetime.DayOfWeek

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    /** Empty means the alarm rings once. */
    val days: Set<DayOfWeek>,
    /** The wake-up games in the order they're played, each at most once. Empty means one tap stops the alarm. */
    val games: List<GameStep>,
    val enabled: Boolean,
    /** An IANA zone id such as "America/New_York" whose clock [hour] and [minute] and [days] follow; null follows the phone. */
    val timeZone: String? = null,
    /** The city the zone was picked from, such as "New York". */
    val zoneLabel: String? = null,
    /** This alarm's own sound; null rings with the one chosen in Settings. */
    val sound: RingSound? = null,
    /**
     * How hard each round of its games is. Null for an alarm from before this was set per alarm, which plays as hard as
     * Settings used to say, or Normal.
     */
    val difficulty: Difficulty? = null,
    /** How long Snooze puts it off, in minutes; 0 means it can't be snoozed. */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** How many times in a row Snooze can put it off. */
    val snoozeLimit: Int = DEFAULT_SNOOZE_LIMIT,
    /** A line the ring screen shows under the name, such as "Gym bag is by the door"; null or blank means none. */
    val description: String? = null,
    /** Rings once, then deletes itself once it's done with: a nap, or "in 20 minutes". It never repeats. */
    val temporary: Boolean = false,
) {
    init {
        require(!temporary || days.isEmpty()) { "a temporary alarm rings once, but had days $days" }
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
        require(games.distinctBy { it.type }.size == games.size) { "each game can be played only once, was $games" }
    }

    val repeats: Boolean get() = days.isNotEmpty()
}

/** What an alarm is called until it's given a name. */
const val DEFAULT_ALARM_NAME = "Alarm"

/** The longest name and description the Edit screen takes. */
const val MAX_NAME_LENGTH = 30
const val MAX_DESCRIPTION_LENGTH = 120

/** The alarm as saved: its name trimmed, [DEFAULT_ALARM_NAME] when left blank, and a blank description dropped. */
fun Alarm.tidied(): Alarm = copy(
    label = label.trim().ifEmpty { DEFAULT_ALARM_NAME },
    description = description?.trim()?.takeIf(String::isNotEmpty),
)
