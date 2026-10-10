package app.morrow.ui.mission

import app.morrow.domain.MissionType
import app.morrow.missions.TypeState
import app.morrow.missions.UnscrambleState
import app.morrow.ui.gameTitle

fun typeModel(state: TypeState, overall: Float = state.progress) = MissionModel(
    title = MissionType.TYPE.gameTitle,
    count = "${minOf(state.finished + 1, state.rounds)} of ${state.rounds}",
    pipLine = pipLine(overall),
)

fun unscrambleModel(state: UnscrambleState, overall: Float = state.progress) = MissionModel(
    title = MissionType.UNSCRAMBLE.gameTitle,
    count = "${minOf(state.finished + 1, state.rounds)} of ${state.rounds}",
    pipLine = pipLine(overall),
)

fun typeHint(state: TypeState): String = when {
    state.typoWord != null -> "Check “${state.typoWord}”. It doesn’t match yet."
    state.typed.isBlank() -> "Type it exactly. Capitals and the full stop don’t matter."
    else -> "Looking good. Keep going."
}

fun unscrambleHint(state: UnscrambleState): String = when {
    state.wrongIndex != null && state.placed > 0 -> "Not that one yet. What comes after “${state.words[state.placed - 1]}”?"
    state.wrongIndex != null -> "Not that one yet. Which word starts the sentence?"
    state.placed == 0 -> "Tap the first word of the sentence."
    else -> "Now the next word."
}

/** What a screen reader says for the sentence built so far. */
fun unscrambleSoFar(state: UnscrambleState): String =
    "So far: " + state.words.take(state.placed).joinToString(" ").ifEmpty { "nothing yet" }
