package app.morrow.ui.home

import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.domain.SleepMath
import app.morrow.domain.nextAlarm
import app.morrow.domain.nextRing
import app.morrow.platform.AlarmPermission
import app.morrow.theme.sunElevation
import app.morrow.ui.displayName
import app.morrow.ui.edit.zoneName
import app.morrow.ui.formatDuration
import app.morrow.ui.gamesText
import app.morrow.ui.hhmm
import app.morrow.ui.world.cityClock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Everything Home shows, worked out from the alarms, the time and the missing permissions. */
data class HomeModel(
    val dateLabel: String,
    val greeting: String,
    /** The Morning screen's answer, until the next alarm rings. */
    val lookingForward: String?,
    /** Null when there are no alarms at all; the empty state takes its place. */
    val sleepCard: SleepCard?,
    /** Why alarms can't ring, if they can't. */
    val banner: String?,
    val alarmCount: String,
    val rows: List<AlarmRow>,
)

data class SleepCard(val lead: String, val big: String, val detail: String, val night: Boolean)

data class AlarmRow(
    val id: Long,
    val time: String,
    val details: String,
    /** For the row's icon. */
    val firstGame: MissionType,
    /** The first game, and how many more follow, such as "Sums +1". */
    val games: String,
    val enabled: Boolean,
    /** Whether the sun is up at the alarm's time, for its little sun or moon icon. */
    val sunUp: Boolean,
    /** For an alarm on another city's clock, that city and when it rings here, like "New York time · 14:00 here". */
    val zoneText: String? = null,
)

fun homeModel(alarms: List<Alarm>, now: Instant, zone: TimeZone, missing: List<AlarmPermission>, intention: String? = null): HomeModel {
    val local = now.toLocalDateTime(zone)
    val minute = local.hour * 60 + local.minute
    val blockers = missing.filter { it.required }
    return HomeModel(
        dateLabel = "${local.dayOfWeek.fullName()} ${local.day} ${local.month.name.lowercase().replaceFirstChar(Char::uppercase)}",
        greeting = when {
            minute < 5 * 60 -> "Still up"
            minute < 12 * 60 -> "Good morning"
            minute < 17 * 60 -> "Good afternoon"
            minute < 21 * 60 -> "Good evening"
            else -> "Good night"
        },
        lookingForward = intention?.takeIf(String::isNotBlank)?.let { "Looking forward to $it" },
        sleepCard = if (alarms.isEmpty()) null else sleepCard(alarms, now, zone, blocked = blockers.isNotEmpty()),
        banner = bannerText(blockers),
        alarmCount = if (alarms.isEmpty()) "" else "${alarms.count { it.enabled }} of ${alarms.size} on",
        rows = alarms.map { alarm ->
            AlarmRow(
                id = alarm.id,
                time = hhmm(alarm.hour, alarm.minute),
                details = "${alarm.label} · ${describeDays(alarm.days)}",
                firstGame = alarm.games.first().type,
                games = alarm.games.first().type.displayName + if (alarm.games.size > 1) " +${alarm.games.size - 1}" else "",
                enabled = alarm.enabled,
                sunUp = sunElevation((alarm.hour * 60 + alarm.minute).toFloat()) >= 0,
                zoneText = alarm.zoneName()?.let { city -> "$city time · ${cityClock(alarm.nextRing(now, zone), zone)} here" },
            )
        },
    )
}

private fun sleepCard(alarms: List<Alarm>, now: Instant, zone: TimeZone, blocked: Boolean): SleepCard {
    val next = alarms.nextAlarm(now, zone)
        ?: return SleepCard("No alarms are on", "Sleep in", "Pip will let you rest. Switch an alarm on when you need one.", night = false)

    val local = now.toLocalDateTime(zone)
    val minute = local.hour * 60 + local.minute
    val alarm = next.alarm
    val until = next.at - now
    val at = next.at.toLocalDateTime(zone)
    // The time here, which for an alarm on another city's clock isn't the time it was set for.
    val time = hhmm(at.hour, at.minute)
    val day = when (local.date.daysUntil(at.date)) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "on ${at.dayOfWeek.fullName()}"
    }
    val night = minute >= 20 * 60 || minute < 4 * 60
    val card = when {
        until >= 1.days -> SleepCard(
            lead = "Next wake-up",
            big = "${at.dayOfWeek.shortName()} $time",
            detail = "${alarm.label} · ${gamesText(alarm.games)}. Pip will be up before you.",
            night = night,
        )
        night && until <= 14.hours -> {
            val cycles = SleepMath.fullCycles(until)
            SleepCard(
                lead = "Sleep now and you’ll get",
                big = formatDuration(until),
                detail = "That’s about $cycles full sleep cycle${if (cycles == 1) "" else "s"}. Pip wakes you $day at $time.",
                night = true,
            )
        }
        else -> SleepCard(
            lead = "Pip wakes you in",
            big = formatDuration(until),
            detail = "${alarm.label}, $day at $time · ${gamesText(alarm.games)}",
            night = night,
        )
    }
    return if (blocked) card.copy(lead = "Paused until alarms are allowed") else card
}

private fun bannerText(blockers: List<AlarmPermission>): String? = when {
    blockers.isEmpty() -> null
    blockers == listOf(AlarmPermission.EXACT_ALARMS) ->
        "Morrow lost permission to set exact alarms. Nothing will go off until you allow it again."
    blockers == listOf(AlarmPermission.ALARMS) ->
        "Morrow isn’t allowed to set alarms. Nothing will go off until you allow it."
    else -> {
        val names = blockers.map { it.title.lowercase() }
        val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
        "Morrow needs $list before any alarm can go off."
    }
}

fun describeDays(days: Set<DayOfWeek>): String = when (days) {
    emptySet<DayOfWeek>() -> "Once"
    DayOfWeek.entries.toSet() -> "Every day"
    DayOfWeek.entries.take(5).toSet() -> "Weekdays"
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
    else -> days.sorted().joinToString(" ") { it.shortName() }
}

private fun DayOfWeek.fullName() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun DayOfWeek.shortName() = fullName().take(3)
