package app.morrow.ui.mission

import app.morrow.missions.BREATHS
import app.morrow.missions.BreatheNote
import app.morrow.missions.BreatheState
import app.morrow.missions.ScanState
import app.morrow.missions.ShakeState
import app.morrow.missions.SumsState

/** The mission screen's header: its title, a progress count, and what Pip says. */
data class MissionModel(val title: String, val count: String, val pipLine: String)

fun sumsModel(state: SumsState) = MissionModel(
    title = "Wake-up sums",
    count = "${minOf(state.solved + 1, state.count)} of ${state.count}",
    pipLine = pipLine(state.progress),
)

fun breatheModel(state: BreatheState) = MissionModel(
    title = "Three slow breaths",
    count = "${state.breaths} of $BREATHS",
    pipLine = pipLine(state.progress),
)

fun shakeModel(state: ShakeState) = MissionModel(
    title = "Shake it off",
    count = "${state.shakes} of ${state.goal}",
    pipLine = pipLine(state.progress),
)

fun scanModel(state: ScanState, cameraAllowed: Boolean) = MissionModel(
    title = "Kettle hunt",
    count = when {
        state.found -> "Found it"
        !cameraAllowed -> "Camera off"
        state.wrong > 0 -> "Try again"
        else -> "Find the code"
    },
    pipLine = when {
        state.found -> pipLine(1f)
        !cameraAllowed -> "I can’t see a thing without the camera!"
        state.wrong > 0 -> "Hmm, that’s not our kettle."
        else -> pipLine(0f)
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
    BreatheNote.TWO_MORE -> "Lovely. Two more."
    BreatheNote.ONE_MORE -> "One more, nice and slow."
    null -> "Hold Pip for one slow breath, about four seconds. Let go too early and it starts again."
}

fun scanText(state: ScanState, cameraAllowed: Boolean): String = when {
    state.found -> "That’s the one. Tea’s on."
    !cameraAllowed -> "Pip needs the camera to find your kettle code. The alarm keeps singing until a mission is done."
    state.savedCode == null -> "Point the camera at the code on your kettle, and Pip will remember it for next time."
    state.wrong > 0 -> "That’s a different code. Look for the sticker you saved on your kettle."
    else -> "Walk to the kitchen and point the camera at the code on your kettle."
}
