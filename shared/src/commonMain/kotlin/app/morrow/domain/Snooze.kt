package app.morrow.domain

/** The snooze lengths an alarm can have, in minutes; 0 means it can't be snoozed. */
val SnoozeChoices = listOf(0, 5, 10, 15)

const val DEFAULT_SNOOZE_MINUTES = 5

/** How many times in a row an alarm can snooze; after that only its games stop it. */
val SnoozeLimitChoices = listOf(1, 2, 3, 5, 10)

const val DEFAULT_SNOOZE_LIMIT = 3

/** Whether [alarm], snoozed [snoozed] times so far, can be snoozed again. */
fun canSnooze(alarm: Alarm, snoozed: Int): Boolean = alarm.snoozeMinutes > 0 && snoozed < alarm.snoozeLimit
