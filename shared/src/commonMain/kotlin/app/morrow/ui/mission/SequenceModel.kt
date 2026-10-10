package app.morrow.ui.mission

import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.ui.displayName
import app.morrow.ui.gameTitle

enum class ChipState { DONE, NOW, NEXT }

/** One game in the header's row of chips. */
data class StepChip(val type: MissionType, val state: ChipState, val label: String)

/** How far through the whole sequence: the [finished] games, plus [current] of the one being played, out of [steps]. */
fun sequenceProgress(finished: Int, current: Float, steps: Int): Float =
    ((finished + current.coerceIn(0f, 1f)) / steps).coerceIn(0f, 1f)

/** A chip per game; the one at [index] counts as done once [stepDone]. */
fun stepChips(steps: List<GameStep>, index: Int, stepDone: Boolean): List<StepChip> = steps.mapIndexed { i, step ->
    val state = when {
        i < index || (i == index && stepDone) -> ChipState.DONE
        i == index -> ChipState.NOW
        else -> ChipState.NEXT
    }
    StepChip(step.type, state, "Game ${i + 1} of ${steps.size}, ${step.type.displayName}, ${state.name.lowercase()}")
}

/** What Pip says between games. */
fun nextUpLine(next: MissionType): String = "Nice one! Next up: ${next.gameTitle.lowercase()}."

/** When the game at [index] can't be played on this phone, three sums take its place and the rest stay as they were. */
fun switchedToSums(steps: List<GameStep>, index: Int): List<GameStep> =
    steps.mapIndexed { i, step -> if (i == index) GameStep(MissionType.SUMS, 3) else step }
