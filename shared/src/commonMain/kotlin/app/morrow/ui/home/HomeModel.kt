package app.morrow.ui.home

import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.domain.SleepMath
import app.morrow.domain.nextAlarm
import app.morrow.platform.AlarmPermission
import app.morrow.theme.sunElevation
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Everything Home shows, worked out from the alarms, the time and the missing permissions. */
data class HomeModel(
    val dateLabel: String,
    val greeting: String,
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
    val mission: MissionType,
    val enabled: Boolean,
    /** Whether the sun is up at the alarm's time, for its little sun or moon icon. */
    val sunUp: Boolean,
)

fun homeModel(alarms: List<Alarm>, now: Instant, zone: TimeZone, missing: List<AlarmPermission>): HomeModel {
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
        sleepCard = if (alarms.isEmpty()) null else sleepCard(alarms, now, zone, blocked = blockers.isNotEmpty()),
        banner = bannerText(blockers),
        alarmCount = if (alarms.isEmpty()) "" else "${alarms.count { it.enabled }} of ${alarms.size} on",
        rows = alarms.map { alarm ->
            AlarmRow(
                id = alarm.id,
                time = hhmm(alarm.hour, alarm.minute),
                details = "${alarm.label} · ${describeDays(alarm.days)}",
                mission = alarm.mission,
                enabled = alarm.enabled,
                sunUp = sunElevation((alarm.hour * 60 + alarm.minute).toFloat()) >= 0,
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
    val time = hhmm(alarm.hour, alarm.minute)
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
            detail = "${alarm.label} · ${alarm.mission.displayName} mission. Pip will be up before you.",
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
            detail = "${alarm.label}, $day at $time · ${alarm.mission.displayName} mission",
            night = night,
        )
    }
    return if (blocked) card.copy(lead = "Paused until alarms are allowed") else card
}

private fun bannerText(blockers: List<AlarmPermission>): String? = when {
    blockers.isEmpty() -> null
    blockers == listOf(AlarmPermission.EXACT_ALARMS) ->
        "Morrow lost permission to set exact alarms. Nothing will go off until you allow it again."
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

val MissionType.displayName: String
    get() = when (this) {
        MissionType.SUMS -> "Sums"
        MissionType.BREATHE -> "Breathe"
        MissionType.SHAKE -> "Shake"
        MissionType.SCAN -> "Scan"
    }

/** Rounds up to the minute, so a wake-up 7 h 19 min 59 s away reads as the clock suggests: 7h 20m. */
private fun formatDuration(duration: Duration): String {
    val totalMinutes = (duration.inWholeMilliseconds + 59_999) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "$minutes min"
}

private fun hhmm(hour: Int, minute: Int) = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun DayOfWeek.fullName() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun DayOfWeek.shortName() = fullName().take(3)
