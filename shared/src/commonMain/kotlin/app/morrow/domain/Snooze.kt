package app.morrow.domain

/** The snooze lengths an alarm can have, in minutes; 0 means it can't be snoozed. */
val SnoozeChoices = listOf(0, 5, 10, 15)

const val DEFAULT_SNOOZE_MINUTES = 5

/** An alarm snoozes at most this many times in a row; after that only its games stop it. */
const val MAX_SNOOZES = 3

/** Whether [alarm], snoozed [snoozed] times so far, can be snoozed again. */
fun canSnooze(alarm: Alarm, snoozed: Int): Boolean = alarm.snoozeMinutes > 0 && snoozed < MAX_SNOOZES
