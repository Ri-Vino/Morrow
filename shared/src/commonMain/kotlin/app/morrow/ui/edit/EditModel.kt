package app.morrow.ui.edit

import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.rules
import app.morrow.ui.displayName
import app.morrow.ui.formatDuration
import app.morrow.ui.gameDescription
import app.morrow.ui.hhmm
import app.morrow.ui.roundsText
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

private const val MINUTES_PER_DAY = 24 * 60

/** The dial moves in steps this size. */
const val DIAL_STEP_MINUTES = 5

data class EditModel(
    val title: String,
    val canDelete: Boolean,
    val time: String,
    val daypart: String,
    val untilText: String,
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

data class GameChoice(val type: MissionType, val name: String, val description: String, val added: Boolean)

/** How long the games take, and whether that's [tooLong] for the alarm's ten minutes of singing. */
data class Estimate(val text: String, val tooLong: Boolean)

fun editModel(draft: Alarm, nowMinute: Int): EditModel {
    val alarmMinute = draft.hour * 60 + draft.minute
    val until = (alarmMinute - nowMinute).mod(MINUTES_PER_DAY).takeIf { it != 0 } ?: MINUTES_PER_DAY
    val night = nowMinute >= 20 * 60 || nowMinute < 4 * 60
    val games = draft.games
    return EditModel(
        title = if (draft.id == 0L) "New alarm" else "Edit alarm",
        canDelete = draft.id != 0L,
        time = hhmm(draft.hour, draft.minute),
        daypart = daypart(alarmMinute),
        untilText = if (night) "Sleep now and that’s ${formatDuration(until)} of rest" else "That’s ${formatDuration(until)} from now",
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
