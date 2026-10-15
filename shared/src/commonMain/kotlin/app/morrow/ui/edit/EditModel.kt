package app.morrow.ui.edit

import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.WakeSound
import app.morrow.domain.clockZone
import app.morrow.domain.nextOccurrence
import app.morrow.domain.ownZone
import app.morrow.domain.rules
import app.morrow.domain.timeZoneOrNull
import app.morrow.places.City
import app.morrow.places.SavedCity
import app.morrow.ui.displayName
import app.morrow.ui.formatDuration
import app.morrow.ui.gameDescription
import app.morrow.ui.hhmm
import app.morrow.ui.roundsText
import app.morrow.ui.world.cityClock
import app.morrow.ui.world.cityMinute
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.time.Instant

private const val MINUTES_PER_DAY = 24 * 60

/** The dial moves in steps this size. */
const val DIAL_STEP_MINUTES = 5

data class EditModel(
    val title: String,
    val canDelete: Boolean,
    /** On the alarm's own clock: the city's, when it has one. */
    val time: String,
    val daypart: String,
    val untilText: String,
    val zone: ZoneRow,
    val sound: SoundRow,
    /** The Alarm sound sheet: Default first, then every sound. */
    val soundChoices: List<SoundChoice>,
    /** The wake-up games in play order. */
    val games: List<GameRow>,
    val estimate: Estimate,
    /** Every game, for the Add a game sheet. */
    val gameChoices: List<GameChoice>,
    val canAddGame: Boolean,
)

data class GameRow(
    val type: MissionType,
    /** Its place in the order, from 1. */
    val step: Int,
    val name: String,
    val roundsText: String,
    val rounds: Int,
    /** Whether it has a − n + stepper; a game of one round has none. */
    val counted: Boolean,
    val canFewer: Boolean,
    val canMore: Boolean,
    /** The last game left can't be removed. */
    val removable: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

/** The Time zone row: whose clock the alarm keeps. */
data class ZoneRow(val title: String, val subtitle: String, val zoned: Boolean)

/** The alarm's sound, [own] when it has one rather than the one chosen in Settings. */
data class SoundRow(val title: String, val subtitle: String, val own: Boolean)

/** A choice in the Alarm sound sheet. Picking it sets the alarm's [sound], null for the one chosen in Settings. */
data class SoundChoice(val sound: WakeSound?, val name: String, val subtitle: String, val selected: Boolean)

/** A choice in the "Ring by the time in…" sheet. Picking it sets the alarm's [timeZone] and [city], both null for the phone's. */
data class ZoneChoice(
    val timeZone: String?,
    val city: String?,
    val name: String,
    val subtitle: String,
    /** The time there now. */
    val time: String,
    val selected: Boolean,
)

data class GameChoice(val type: MissionType, val name: String, val description: String, val added: Boolean)

/** How long the games take, and whether that's [tooLong] for the alarm's ten minutes of singing. */
data class Estimate(val text: String, val tooLong: Boolean)

/** [now] and [phoneZone] are the phone's; an alarm on another city's clock is shown by that clock. */
/** [defaultSound] is the one chosen in Settings, which alarms without their own ring with. */
fun editModel(draft: Alarm, now: Instant, phoneZone: TimeZone, defaultSound: WakeSound = WakeSound.BIRDSONG): EditModel {
    val alarmMinute = draft.hour * 60 + draft.minute
    val nowMinute = cityMinute(now, phoneZone)
    val city = draft.zoneName()
    // The next time the alarm's clock shows its time, whatever the repeat days.
    val ringsAt = draft.copy(days = emptySet()).nextOccurrence(now, draft.clockZone(phoneZone))
    val until = ringsAt - now
    val night = nowMinute >= 20 * 60 || nowMinute < 4 * 60
    val rest = if (night) "Sleep now and that’s ${formatDuration(until)} of rest" else "That’s ${formatDuration(until)} from now"
    val games = draft.games
    return EditModel(
        title = if (draft.id == 0L) "New alarm" else "Edit alarm",
        canDelete = draft.id != 0L,
        time = hhmm(draft.hour, draft.minute),
        daypart = if (city == null) daypart(alarmMinute) else "$city · ${daypart(alarmMinute)}",
        untilText = if (city == null) rest else "Rings at ${cityClock(ringsAt, phoneZone)} your time · $rest",
        zone = if (city == null) {
            ZoneRow("Phone’s time", "Rings by the clock wherever you are", zoned = false)
        } else {
            ZoneRow("$city time", "Follows $city’s clock, summer time included", zoned = true)
        },
        sound = draft.sound?.let { SoundRow(it.displayName, "Just for this alarm", own = true) }
            ?: SoundRow(defaultSound.displayName, "Default, as chosen in Settings", own = false),
        soundChoices = listOf(SoundChoice(null, "Default", "${defaultSound.displayName}, as chosen in Settings", draft.sound == null)) +
            WakeSound.entries.map { SoundChoice(it, it.displayName, it.description, draft.sound == it) },
        games = games.mapIndexed { i, step ->
            val max = step.type.rules.maxRounds
            GameRow(
                type = step.type,
                step = i + 1,
                name = step.type.displayName,
                roundsText = roundsText(step),
                rounds = step.rounds,
                counted = max > 1,
                canFewer = step.rounds > 1,
                canMore = step.rounds < max,
                removable = games.size > 1,
                canMoveUp = i > 0,
                canMoveDown = i < games.lastIndex,
            )
        },
        estimate = estimate(gamesSeconds(games)),
        gameChoices = MissionType.entries.map { type ->
            GameChoice(type, type.displayName, type.gameDescription, added = games.any { it.type == type })
        },
        canAddGame = games.size < MissionType.entries.size,
    )
}

/** The city whose clock the alarm keeps, or null when it follows the phone, as it does when its zone isn't known here. */
fun Alarm.zoneName(): String? {
    if (ownZone() == null) return null
    return zoneLabel?.takeIf(String::isNotBlank) ?: timeZone?.substringAfterLast('/')?.replace('_', ' ')
}

/** The alarm set to keep [timeZone]'s clock, picked as [city]; both null to follow the phone. */
fun Alarm.inZone(timeZone: String?, city: String?): Alarm =
    copy(timeZone = timeZone, zoneLabel = city.takeIf { timeZone != null })

/**
 * The "Ring by the time in…" choices: the phone's time, which [phoneCity] names, then each [saved] city on another
 * clock. A zone picked by search, which isn't among them, is listed second so the choice shows.
 */
fun zoneChoices(draft: Alarm, saved: List<City>, now: Instant, phoneZone: TimeZone, phoneCity: String): List<ZoneChoice> {
    val zoned = draft.zoneName() != null
    val phone = ZoneChoice(
        timeZone = null,
        city = null,
        name = "Phone’s time",
        subtitle = "$phoneCity now · moves with you when you travel",
        time = cityClock(now, phoneZone),
        selected = !zoned,
    )
    val cities = saved
        .filter { it.timeZone != phoneZone.id }
        .distinctBy { it.timeZone to it.name }
        .mapNotNull { city ->
            val zone = zoneOrNull(city.timeZone) ?: return@mapNotNull null
            ZoneChoice(
                timeZone = city.timeZone,
                city = city.name,
                name = "${city.name} time",
                subtitle = offsetFromYou(now, zone, phoneZone),
                time = cityClock(now, zone),
                selected = zoned && draft.timeZone == city.timeZone && draft.zoneLabel == city.name,
            )
        }
    val picked = draft.ownZone()?.takeIf { zoned && cities.none(ZoneChoice::selected) }?.let { zone ->
        val name = draft.zoneName()
        ZoneChoice(draft.timeZone, name, "$name time", offsetFromYou(now, zone, phoneZone), cityClock(now, zone), selected = true)
    }
    return listOf(phone) + listOfNotNull(picked) + cities
}

/** Cities found by "Find another city", each with where it is and how its clock sits against the phone's. */
fun searchChoices(found: List<City>, now: Instant, phoneZone: TimeZone): List<ZoneChoice> =
    found.mapNotNull { city ->
        val zone = zoneOrNull(city.timeZone) ?: return@mapNotNull null
        val place = listOfNotNull(city.region, city.country).distinct().joinToString(", ")
        ZoneChoice(
            timeZone = city.timeZone,
            city = city.name,
            name = city.name,
            subtitle = "$place · ${offsetFromYou(now, zone, phoneZone)}",
            time = cityClock(now, zone),
            selected = false,
        )
    }

/**
 * The name for the phone's own zone: a saved city in it, home first, else the catalog's city for it ([zoneCity]),
 * else the zone id.
 */
fun phoneCityName(saved: List<SavedCity>, phoneZone: TimeZone, zoneCity: City?): String =
    saved.filter { it.city.timeZone == phoneZone.id }.maxByOrNull { it.isHome }?.city?.name
        ?: zoneCity?.name
        ?: phoneZone.id

/** How [zone]'s clock sits against the phone's at [now], like "−5 h from you" or "+5½ h from you". */
fun offsetFromYou(now: Instant, zone: TimeZone, phoneZone: TimeZone): String {
    val minutes = (now.offsetIn(zone).totalSeconds - now.offsetIn(phoneZone).totalSeconds) / 60
    if (minutes == 0) return "Same time as you now"
    val sign = if (minutes > 0) "+" else "−"
    val hours = abs(minutes) / 60
    val offset = when (val rest = abs(minutes) % 60) {
        0 -> "$sign$hours h"
        30 -> "$sign$hours½ h"
        else -> "$sign$hours h $rest m"
    }
    return "$offset from you"
}

private fun zoneOrNull(id: String): TimeZone? = timeZoneOrNull(id)

/** A rough guess at how long [games] take to play, in seconds. */
fun gamesSeconds(games: List<GameStep>): Int = games.sumOf { it.type.rules.secondsPerRound * it.rounds }

/** Over this, the games may outlast the alarm, which stops singing at ten minutes. */
private const val TOO_LONG_SECONDS = 8 * 60

fun estimate(seconds: Int): Estimate {
    val minutes = maxOf(1, (seconds / 60f).roundToInt())
    val tooLong = seconds > TOO_LONG_SECONDS
    return Estimate(
        text = if (tooLong) "About $minutes min. Pip stops singing at 10, so keep it shorter." else "About $minutes min to finish",
        tooLong = tooLong,
    )
}

/** [type] played for [rounds], kept within its limits. */
fun Alarm.withRounds(type: MissionType, rounds: Int): Alarm =
    copy(games = games.map { if (it.type == type) GameStep(type, rounds.coerceIn(1, type.rules.maxRounds)) else it })

/** [type] added at the end with its usual rounds, unless it's already there. */
fun Alarm.withGame(type: MissionType): Alarm =
    if (games.any { it.type == type }) this else copy(games = games + GameStep(type))

/** [type] taken out, unless it's the only game. */
fun Alarm.withoutGame(type: MissionType): Alarm =
    if (games.size == 1) this else copy(games = games.filter { it.type != type })

/** [type] moved [by] places, earlier when negative, stopping at either end. */
fun Alarm.withGameMoved(type: MissionType, by: Int): Alarm {
    val from = games.indexOfFirst { it.type == type }
    if (from < 0) return this
    val to = (from + by).coerceIn(0, games.lastIndex)
    if (to == from) return this
    val moved = games.toMutableList().apply { add(to, removeAt(from)) }
    return copy(games = moved)
}

fun daypart(minuteOfDay: Int): String = when {
    minuteOfDay < 5 * 60 -> "Deep night"
    minuteOfDay < 6 * 60 -> "Before dawn"
    minuteOfDay < 7 * 60 -> "Dawn"
    minuteOfDay < 11 * 60 -> "Morning"
    minuteOfDay < 14 * 60 -> "Midday"
    minuteOfDay < 17 * 60 -> "Afternoon"
    minuteOfDay < 18 * 60 + 30 -> "Golden hour"
    minuteOfDay < 20 * 60 -> "Dusk"
    else -> "Night"
}

/**
 * The time at a point on the dial, given as an offset from its centre with y pointing down.
 * Midnight is at the top and time runs clockwise, snapped to [DIAL_STEP_MINUTES].
 */
fun dialMinute(dx: Float, dy: Float): Int {
    var angle = atan2(dx.toDouble(), -dy.toDouble())
    if (angle < 0) angle += 2 * PI
    val steps = (angle / (2 * PI) * MINUTES_PER_DAY / DIAL_STEP_MINUTES).roundToInt()
    return (steps * DIAL_STEP_MINUTES) % MINUTES_PER_DAY
}

fun Alarm.nudged(byMinutes: Int): Alarm {
    val minute = (hour * 60 + minute + byMinutes).mod(MINUTES_PER_DAY)
    return copy(hour = minute / 60, minute = minute % 60)
}

fun Alarm.at(minuteOfDay: Int): Alarm = copy(hour = minuteOfDay / 60, minute = minuteOfDay % 60)

/** The minute of the day typed into the Set the time sheet, or null until it's a real time. Empty minutes are :00. */
fun typedTime(hours: String, minutes: String): Int? {
    val hour = hours.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = if (minutes.isEmpty()) 0 else minutes.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return hour * 60 + minute
}

private val WakeSound.description: String
    get() = when (this) {
        WakeSound.BIRDSONG -> "Birds waking up with you"
        WakeSound.WIND_CHIMES -> "Chimes on a soft breeze"
        WakeSound.SOFT_PIANO -> "A slow, gentle tune"
    }
