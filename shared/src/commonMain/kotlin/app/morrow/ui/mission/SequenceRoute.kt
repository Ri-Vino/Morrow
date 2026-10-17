package app.morrow.ui.mission

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.morrow.data.MissionSetup
import app.morrow.data.MorrowSettings
import app.morrow.domain.Difficulty
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.SHAKES_PER_ROUND
import app.morrow.domain.decodeGames
import app.morrow.domain.encodeGames
import app.morrow.missions.BreatheMission
import app.morrow.missions.ScanMission
import app.morrow.missions.ShakeDetector
import app.morrow.missions.ShakeMission
import app.morrow.missions.SumsMission
import app.morrow.missions.TypeMission
import app.morrow.missions.UnscrambleMission
import app.morrow.platform.CodeScanner
import app.morrow.platform.MotionSensor
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import kotlinx.coroutines.delay

class MissionServices(val settings: MorrowSettings, val motion: MotionSensor, val scanner: CodeScanner)

/**
 * Plays [games] in order, each for its rounds and at [difficulty], then calls [onDone] once the last one is finished.
 * A null difficulty, from an alarm saved before each had its own, plays as hard as Settings used to say. A game this phone can't play, with the camera off or no motion sensor, can switch to sums, so
 * an alarm can always be finished.
 */
@Composable
fun SequenceRoute(games: List<GameStep>, difficulty: Difficulty?, services: MissionServices, onDone: () -> Unit) {
    val saved by services.settings.missionSetup.collectAsState(initial = null)
    // Starts again if the games change, as when the ringing alarm's own games replace the placeholder's.
    var steps by rememberSaveable(games, stateSaver = StepsSaver) { mutableStateOf(games) }
    var index by rememberSaveable(games) { mutableIntStateOf(0) }
    val loaded = saved?.let { setup -> difficulty?.let { setup.copy(difficulty = it) } ?: setup } ?: return
    val current = steps[index]
    val step = Step(steps, index, onFinished = { if (index == steps.lastIndex) onDone() else index++ })
    val switchToSums = { steps = switchedToSums(steps, index) }
    MorrowTheme(ThemeKind.NIGHT) {
        // A fresh game for every step, including one just switched to sums.
        key(index, current) {
            when (current.type) {
                MissionType.SUMS -> Sums(current.rounds, loaded, step)
                MissionType.TYPE -> Type(current.rounds, loaded, step)
                MissionType.UNSCRAMBLE -> Unscramble(current.rounds, loaded, step)
                MissionType.BREATHE -> Breathe(current.rounds, step)
                MissionType.SHAKE -> Shake(current.rounds, loaded, services.motion, step, switchToSums)
                MissionType.SCAN -> Scan(loaded, services, step, switchToSums)
            }
        }
    }
}

/** Where the game being played sits in the sequence. */
private class Step(val steps: List<GameStep>, val index: Int, val onFinished: () -> Unit) {
    fun overall(progress: Float) = sequenceProgress(index, progress, steps.size)
}

@Composable
private fun Sums(rounds: Int, setup: MissionSetup, step: Step) {
    val mission = remember { SumsMission(rounds, difficulty = setup.difficulty) }
    val state by mission.state.collectAsState()
    StepFrame(step, sumsModel(state, step.overall(state.progress)), state.progress) { SumsBody(state, mission) }
}

@Composable
private fun Type(rounds: Int, setup: MissionSetup, step: Step) {
    val mission = remember { TypeMission(rounds, setup.difficulty) }
    val state by mission.state.collectAsState()
    // The keyboard comes up as the step starts, so the first sentence can be typed straight away.
    StepFrame(step, typeModel(state, step.overall(state.progress)), state.progress) { TypeBody(state, mission, autoFocus = true) }
}

@Composable
private fun Unscramble(rounds: Int, setup: MissionSetup, step: Step) {
    val mission = remember { UnscrambleMission(rounds, setup.difficulty) }
    val state by mission.state.collectAsState()
    StepFrame(step, unscrambleModel(state, step.overall(state.progress)), state.progress) { UnscrambleBody(state, mission) }
}

@Composable
private fun Breathe(rounds: Int, step: Step) {
    val mission = remember { BreatheMission(rounds) }
    val state by mission.state.collectAsState()
    StepFrame(step, breatheModel(state, step.overall(state.progress)), state.progress) { BreatheBody(state, mission) }
}

@Composable
private fun Shake(rounds: Int, setup: MissionSetup, motion: MotionSensor, step: Step, onSwitchToSums: () -> Unit) {
    val mission = remember { ShakeMission(rounds * SHAKES_PER_ROUND, ShakeDetector(threshold = setup.difficulty.shakeForce)) }
    val state by mission.state.collectAsState()
    // Leaving the screen cancels this, which also switches the sensor off.
    LaunchedEffect(mission) { mission.listen(motion) }
    StepFrame(step, shakeModel(state, step.overall(state.progress)), state.progress) {
        ShakeBody(state, motion.available, onSwitchToSums)
    }
}

@Composable
private fun Scan(setup: MissionSetup, services: MissionServices, step: Step, onSwitchToSums: () -> Unit) {
    val mission = remember { ScanMission(setup.kettleCode) }
    val state by mission.state.collectAsState()
    val cameraAllowed by services.scanner.cameraAllowed.collectAsState()
    LifecycleResumeEffect(services.scanner) {
        services.scanner.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.savedCode) {
        val code = state.savedCode
        if (setup.kettleCode == null && code != null) services.settings.setKettleCode(code)
    }
    StepFrame(step, scanModel(state, cameraAllowed, step.overall(state.progress)), state.progress) {
        ScanBody(state, services.scanner, cameraAllowed, onCode = mission::scanned, onSwitchToSums = onSwitchToSums)
    }
}

/**
 * The mission screen for one game of the sequence. Once the game is finished, Pip names the next one for a moment,
 * or after the last one gives "We're up!" a moment on screen, before moving on.
 */
@Composable
private fun StepFrame(step: Step, model: MissionModel, progress: Float, body: @Composable ColumnScope.() -> Unit) {
    val done = progress >= 1f
    val next = step.steps.getOrNull(step.index + 1)
    MissionFrame(
        model = if (done && next != null) model.copy(pipLine = nextUpLine(next.type)) else model,
        progress = step.overall(progress),
        chips = if (step.steps.size > 1) stepChips(step.steps, step.index, done) else emptyList(),
        body = body,
    )
    if (!done) return
    LaunchedEffect(Unit) {
        delay(if (next == null) 900 else 1100)
        step.onFinished()
    }
}

private val StepsSaver = Saver<List<GameStep>, String>(save = { encodeGames(it) }, restore = { decodeGames(it) })
