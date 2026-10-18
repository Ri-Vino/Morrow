package app.morrow.domain

/** The quick snooze lengths an alarm can have, in minutes; 0 means it can't be snoozed. Any length up to [MAX_SNOOZE_MINUTES] can be typed. */
val SnoozeChoices = listOf(0, 5, 10, 15)

const val DEFAULT_SNOOZE_MINUTES = 5

/** The longest an alarm can snooze for, in minutes. */
const val MAX_SNOOZE_MINUTES = 60

/** How many times in a row an alarm can snooze; after that only its games stop it. [UNLIMITED_SNOOZES] never runs out. */
val SnoozeLimitChoices = listOf(1, 2, 3, 5, 10, UNLIMITED_SNOOZES)

const val DEFAULT_SNOOZE_LIMIT = 3

/** A [Alarm.snoozeLimit] of this means it can be snoozed as often as you like. */
const val UNLIMITED_SNOOZES = 0

/** Whether [alarm], snoozed [snoozed] times so far, can be snoozed again. */
fun canSnooze(alarm: Alarm, snoozed: Int): Boolean =
    alarm.snoozeMinutes > 0 && (alarm.snoozeLimit == UNLIMITED_SNOOZES || snoozed < alarm.snoozeLimit)
