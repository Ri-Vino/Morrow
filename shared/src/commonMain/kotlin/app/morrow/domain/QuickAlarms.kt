package app.morrow.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The lengths the Nap sheet offers, in minutes, and the one a nap starts at. */
val NapChoices = listOf(10, 20, 30, 45, 60)
const val DEFAULT_NAP_MINUTES = 20

/** The "In X min" sheet's quick picks, in minutes; any other number from 1 up to [MAX_QUICK_MINUTES] can be typed. */
val QuickChoices = listOf(5, 10, 15, 30, 60)
const val MAX_QUICK_MINUTES = 720

const val NAP_NAME = "Nap"
const val QUICK_ALARM_NAME = "Quick alarm"

/**
 * A temporary alarm [minutes] from [now] on the phone's clock in [zone], to the nearest minute, since alarms ring on
 * the minute. Not yet saved, so its id is 0.
 */
fun quickAlarm(minutes: Int, now: Instant, zone: TimeZone, name: String): Alarm {
    require(minutes >= 1) { "a quick alarm rings at least a minute from now, was $minutes" }
    val at = (now + minutes.minutes + 30.seconds).toLocalDateTime(zone)
    return Alarm(
        id = 0,
        hour = at.hour,
        minute = at.minute,
        label = name,
        days = emptySet(),
        games = DefaultGames,
        enabled = true,
        temporary = true,
    )
}

/**
 * A new temporary alarm for Edit to start from: half an hour from [now], rounded up to the next five minutes, named
 * like any other alarm.
 */
fun newTemporaryAlarm(now: Instant, zone: TimeZone): Alarm {
    val local = (now + 30.minutes).toLocalDateTime(zone)
    val minuteOfDay = (local.hour * 60 + local.minute + 4) / 5 * 5 % (24 * 60)
    return Alarm(
        id = 0,
        hour = minuteOfDay / 60,
        minute = minuteOfDay % 60,
        label = DEFAULT_ALARM_NAME,
        days = emptySet(),
        games = DefaultGames,
        enabled = true,
        temporary = true,
    )
}

/**
 * [typed] with [digit] typed after it, for a number of minutes up to [max]: no leading zero, and a digit that would
 * pass [max] starts the number again.
 */
fun typeMinutes(typed: String, digit: Int, max: Int = MAX_QUICK_MINUTES): String {
    if (digit == 0 && typed.isEmpty()) return typed
    val next = typed + digit
    return if (next.length > max.toString().length || next.toInt() > max) digit.toString().takeIf { digit > 0 }.orEmpty() else next
}
