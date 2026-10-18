package app.morrow.domain

/** One game in an alarm's wake-up sequence, played for [rounds] rounds. */
data class GameStep(val type: MissionType, val rounds: Int) {
    init {
        require(rounds in 1..type.rules.maxRounds) { "$type takes 1..${type.rules.maxRounds} rounds, was $rounds" }
    }

    /** A new step of [type] with its usual number of rounds. */
    constructor(type: MissionType) : this(type, type.rules.defaultRounds)
}

/** How many rounds a game allows and starts with, and roughly how long one round takes, for Edit's estimate. */
data class GameRules(val maxRounds: Int, val defaultRounds: Int, val secondsPerRound: Int)

val MissionType.rules: GameRules
    get() = when (this) {
        MissionType.SUMS -> GameRules(maxRounds = 10, defaultRounds = 3, secondsPerRound = 15)
        MissionType.TYPE -> GameRules(maxRounds = 5, defaultRounds = 2, secondsPerRound = 30)
        MissionType.UNSCRAMBLE -> GameRules(maxRounds = 5, defaultRounds = 2, secondsPerRound = 20)
        MissionType.BREATHE -> GameRules(maxRounds = 5, defaultRounds = 3, secondsPerRound = 6)
        MissionType.SHAKE -> GameRules(maxRounds = 6, defaultRounds = 3, secondsPerRound = 8)
        MissionType.SCAN -> GameRules(maxRounds = 1, defaultRounds = 1, secondsPerRound = 40)
    }

/** One round of Shake is this many shakes. */
const val SHAKES_PER_ROUND = 10

/** What a new alarm plays, and the fallback whenever an alarm's games can't be read. */
val DefaultGames = listOf(GameStep(MissionType.SUMS, 3))

/** What an alarm with no games is stored as, so it can be told from games this version can't read. */
private const val NO_GAMES = "NONE"

/** Games as stored, for example "SUMS:3,BREATHE:2", or "NONE" for an alarm that has none. */
fun encodeGames(games: List<GameStep>): String =
    if (games.isEmpty()) NO_GAMES else games.joinToString(",") { "${it.type.name}:${it.rounds}" }

/** An alarm's games as stored: [DefaultGames] when the text holds none this version can play, unless it says there are none. */
fun readGames(text: String): List<GameStep> =
    if (text == NO_GAMES) emptyList() else decodeGames(text).distinctBy { it.type }.ifEmpty { DefaultGames }

/**
 * Reads [encodeGames]' text. Steps of a game this version doesn't know are left out, and rounds are kept within
 * the game's limits, so an alarm can always ring. Repeated games are kept.
 */
fun decodeGames(text: String): List<GameStep> = text.split(",").mapNotNull { part ->
    val type = MissionType.entries.firstOrNull { it.name == part.substringBefore(":").trim() } ?: return@mapNotNull null
    val rounds = part.substringAfter(":", "").trim().toIntOrNull() ?: type.rules.defaultRounds
    GameStep(type, rounds.coerceIn(1, type.rules.maxRounds))
}
