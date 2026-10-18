package app.morrow.ui.home

import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.domain.SleepMath
import app.morrow.domain.nextAlarm
import app.morrow.domain.nextRing
import app.morrow.platform.AlarmPermission
import app.morrow.theme.sunElevation
import app.morrow.ui.degrees
import app.morrow.ui.displayName
import app.morrow.ui.edit.zoneName
import app.morrow.ui.formatDuration
import app.morrow.ui.gamesText
import app.morrow.ui.hhmm
import app.morrow.ui.world.cityClock
import app.morrow.weather.Feel
import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.WeatherState
import app.morrow.weather.conditionsAt
import app.morrow.weather.feelOf
import app.morrow.weather.forecastAt
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
    /** The weather line under the greeting, and the weather in the sky; null with weather off or none recent enough. */
    val weather: HomeWeather? = null,
    /** What was just deleted, like "07:30 alarm deleted", while it can still be undone. */
    val undo: String? = null,
)

/** The line Home shows, with Undo, once [alarm] has been deleted. */
fun deletedNote(alarm: Alarm): String = "${hhmm(alarm.hour, alarm.minute)} alarm deleted"

/** The weather where you are, as Home shows it. */
data class HomeWeather(
    val sky: Sky,
    /** Whether it's night here, for the moon icon on a clear night. */
    val night: Boolean,
    /** "12° · Rain in London". */
    val text: String,
    /** What screen readers hear for the line, which opens World: "12° and rain in London. Open World". */
    val label: String,
    /** How warm and windy it feels, for the sky's frost or haze and what Pip wears. */
    val feel: Feel = Feel.Mild,
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

fun homeModel(
    alarms: List<Alarm>,
    now: Instant,
    zone: TimeZone,
    missing: List<AlarmPermission>,
    intention: String? = null,
    /** From [homeWeather]. */
    weather: HomeWeather? = null,
): HomeModel {
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
        weather = weather,
    )
}

/**
 * Home's weather line for the weather where you are, at [now] in [zone]. Null, so the line and the sky's weather hide,
 * with weather off, before it loads, or when there's no forecast from the last 24 hours.
 */
fun homeWeather(state: WeatherState?, now: Instant, zone: TimeZone, units: Units): HomeWeather? {
    val forecast = state?.takeIf { it.enabled }?.hereWeather?.forecastAt(now) ?: return null
    val conditions = forecast.conditionsAt(now) ?: return null
    val temperature = degrees(conditions.temperature, units)
    val where = state.here?.name?.let { " in $it" } ?: ""
    val local = now.toLocalDateTime(zone)
    return HomeWeather(
        sky = conditions.sky,
        night = sunElevation(local.hour * 60f + local.minute) < 0,
        text = "$temperature · ${conditions.sky.displayName}$where",
        label = "$temperature and ${conditions.sky.displayName.lowercase()}$where. Open World",
        feel = feelOf(conditions.temperature, conditions.wind),
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
