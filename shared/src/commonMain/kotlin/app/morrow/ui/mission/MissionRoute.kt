package app.morrow.ui.mission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.morrow.data.MissionSetup
import app.morrow.data.MorrowSettings
import app.morrow.domain.MissionType
import app.morrow.missions.BreatheMission
import app.morrow.missions.MissionState
import app.morrow.missions.ScanMission
import app.morrow.missions.ShakeMission
import app.morrow.missions.SumsMission
import app.morrow.platform.CodeScanner
import app.morrow.platform.MotionSensor
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import kotlinx.coroutines.delay

class MissionServices(val settings: MorrowSettings, val motion: MotionSensor, val scanner: CodeScanner)

/**
 * Runs a [type] mission sized by the difficulty setting, then calls [onDone] once it's finished.
 * A Scan mission can switch to sums when the camera is off, so an alarm can always be finished.
 */
@Composable
fun MissionRoute(type: MissionType, services: MissionServices, onDone: () -> Unit) {
    val setup by services.settings.missionSetup.collectAsState(initial = null)
    var kind by rememberSaveable { mutableStateOf(type) }
    val loaded = setup ?: return
    MorrowTheme(ThemeKind.NIGHT) {
        when (kind) {
            MissionType.SUMS -> Sums(loaded, onDone)
            MissionType.BREATHE -> Breathe(onDone)
            MissionType.SHAKE -> Shake(loaded, services.motion, onDone, onSwitchToSums = { kind = MissionType.SUMS })
            MissionType.SCAN -> Scan(loaded, services, onDone, onSwitchToSums = { kind = MissionType.SUMS })
        }
    }
}

@Composable
private fun Sums(setup: MissionSetup, onDone: () -> Unit) {
    val mission = remember { SumsMission(setup.difficulty.sums) }
    val state by mission.state.collectAsState()
    FinishWhenDone(state, onDone)
    MissionFrame(sumsModel(state), state.progress) { SumsBody(state, mission) }
}

@Composable
private fun Breathe(onDone: () -> Unit) {
    val mission = remember { BreatheMission() }
    val state by mission.state.collectAsState()
    FinishWhenDone(state, onDone)
    MissionFrame(breatheModel(state), state.progress) { BreatheBody(state, mission) }
}

@Composable
private fun Shake(setup: MissionSetup, motion: MotionSensor, onDone: () -> Unit, onSwitchToSums: () -> Unit) {
    val mission = remember { ShakeMission(setup.difficulty.shakes) }
    val state by mission.state.collectAsState()
    // Leaving the screen cancels this, which also switches the sensor off.
    LaunchedEffect(mission) { mission.listen(motion) }
    FinishWhenDone(state, onDone)
    MissionFrame(shakeModel(state), state.progress) { ShakeBody(state, motion.available, onSwitchToSums) }
}

@Composable
private fun Scan(setup: MissionSetup, services: MissionServices, onDone: () -> Unit, onSwitchToSums: () -> Unit) {
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
    FinishWhenDone(state, onDone)
    MissionFrame(scanModel(state, cameraAllowed), state.progress) {
        ScanBody(state, services.scanner, cameraAllowed, onCode = mission::scanned, onSwitchToSums = onSwitchToSums)
    }
}

/** Gives Pip's "We're up!" a moment on screen before moving on. */
@Composable
private fun FinishWhenDone(state: MissionState, onDone: () -> Unit) {
    if (!state.done) return
    LaunchedEffect(Unit) {
        delay(1500)
        onDone()
    }
}
