package app.morrow.ui.mission

import app.morrow.domain.MissionType
import app.morrow.missions.BreatheNote
import app.morrow.missions.BreatheState
import app.morrow.missions.ScanState
import app.morrow.missions.ShakeState
import app.morrow.missions.SumsState
import app.morrow.ui.gameTitle

/** The mission screen's header: its title, a progress count, and what Pip says. */
data class MissionModel(val title: String, val count: String, val pipLine: String)

// Pip's line follows [overall], the progress through the whole sequence of games.

fun sumsModel(state: SumsState, overall: Float = state.progress) = MissionModel(
    title = MissionType.SUMS.gameTitle,
    count = "${minOf(state.solved + 1, state.count)} of ${state.count}",
    pipLine = pipLine(overall),
)

fun breatheModel(state: BreatheState, overall: Float = state.progress) = MissionModel(
    title = MissionType.BREATHE.gameTitle,
    count = "${state.breaths} of ${state.count}",
    pipLine = pipLine(overall),
)

fun shakeModel(state: ShakeState, overall: Float = state.progress) = MissionModel(
    title = MissionType.SHAKE.gameTitle,
    count = "${state.shakes} of ${state.goal}",
    pipLine = pipLine(overall),
)

fun scanModel(state: ScanState, cameraAllowed: Boolean, overall: Float = state.progress) = MissionModel(
    title = MissionType.SCAN.gameTitle,
    count = when {
        state.found -> "Found it"
        !cameraAllowed -> "Camera off"
        state.wrong > 0 -> "Try again"
        else -> "Find the code"
    },
    pipLine = when {
        state.found -> pipLine(overall)
        !cameraAllowed -> "I can’t see a thing without the camera!"
        state.wrong > 0 -> "Hmm, that’s not our code."
        else -> pipLine(overall)
    },
)

/** Pip wakes up a little more with each step. */
fun pipLine(progress: Float): String = when {
    progress >= 1f -> "We’re up! Well done."
    progress < 0.01f -> "Mmm… five more minutes?"
    progress < 0.4f -> "Okay, okay. I’m stirring."
    progress < 0.75f -> "Ooh, I can see the light."
    else -> "Nearly there, keep going!"
}

fun breatheLabel(state: BreatheState): String = when {
    state.done -> "Beautiful."
    !state.holding -> "Press and hold"
    state.hold < 0.5f -> "Breathe in…"
    else -> "And out…"
}

fun breatheHint(state: BreatheState): String = when (state.note) {
    BreatheNote.TOO_QUICK -> "Too quick. Hold for the whole breath, in and out."
    BreatheNote.MORE -> "Lovely. ${NumberWords.getOrElse(state.left) { state.left.toString() }} more."
    BreatheNote.TWO_MORE -> "Lovely. Two more."
    BreatheNote.ONE_MORE -> "One more, nice and slow."
    null -> "Hold Pip for one slow breath, about four seconds. Let go too early and it starts again."
}

fun scanText(state: ScanState, cameraAllowed: Boolean): String = when {
    state.found -> "That’s the one. Tea’s on."
    !cameraAllowed -> "Pip needs the camera to find your code. The alarm keeps singing until a mission is done."
    state.savedCode == null -> "Point the camera at any QR code or barcode, and Pip will remember it for next time."
    state.wrong > 0 -> "That’s a different code. Look for the one you saved."
    else -> "Walk over to your code and point the camera at it."
}

private val NumberWords = listOf("No", "One", "Two", "Three", "Four", "Five")
