package app.morrow.ui

import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.SHAKES_PER_ROUND

val MissionType.displayName: String
    get() = when (this) {
        MissionType.SUMS -> "Sums"
        MissionType.TYPE -> "Type"
        MissionType.UNSCRAMBLE -> "Unscramble"
        MissionType.BREATHE -> "Breathe"
        MissionType.SHAKE -> "Shake"
        MissionType.SCAN -> "Scan"
    }

/** The heading while the game is played. */
val MissionType.gameTitle: String
    get() = when (this) {
        MissionType.SUMS -> "Wake-up sums"
        MissionType.TYPE -> "Type it out"
        MissionType.UNSCRAMBLE -> "Unscramble"
        MissionType.BREATHE -> "Slow breaths"
        MissionType.SHAKE -> "Shake it off"
        MissionType.SCAN -> "Code hunt"
    }

/** One line about the game, in the Add a game sheet. */
val MissionType.gameDescription: String
    get() = when (this) {
        MissionType.SUMS -> "Quick mental maths. Best if you sleep through anything."
        MissionType.TYPE -> "Copy a short, cheerful sentence exactly."
        MissionType.UNSCRAMBLE -> "Tap the words back into the right order."
        MissionType.BREATHE -> "Hold Pip for slow breaths. The gentlest way up."
        MissionType.SHAKE -> "Shake your phone to get the blood moving."
        MissionType.SCAN -> "Walk to your QR code or barcode and scan it. Up you get."
    }

/** What a step asks for, such as "3 sums" or "30 shakes". */
fun roundsText(step: GameStep): String {
    val n = step.rounds
    fun counted(word: String) = "$n $word${if (n == 1) "" else "s"}"
    return when (step.type) {
        MissionType.SUMS -> counted("sum")
        MissionType.TYPE, MissionType.UNSCRAMBLE -> counted("sentence")
        MissionType.BREATHE -> counted("slow breath")
        MissionType.SHAKE -> "${n * SHAKES_PER_ROUND} shakes"
        MissionType.SCAN -> "Your code"
    }
}

/** The games' names in play order, such as "Sums, Breathe and Scan", or "No games". */
fun gamesText(games: List<GameStep>): String {
    val names = games.map { it.type.displayName }
    if (names.isEmpty()) return "No games"
    return if (names.size < 2) names.joinToString("") else names.dropLast(1).joinToString(", ") + " and " + names.last()
}
