package app.morrow.ui.mission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import app.morrow.data.MorrowSettings
import app.morrow.data.createSettingsStore
import app.morrow.domain.MissionType
import app.morrow.missions.BreatheMission
import app.morrow.missions.BreatheNote
import app.morrow.platform.Acceleration
import app.morrow.platform.CodeScanner
import app.morrow.platform.MotionSensor
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The alarm stops when a mission calls done, so each mission must call it once it's finished and not before. */
@RunWith(RobolectricTestRunner::class)
class MissionRouteTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    @get:Rule
    val folder = TemporaryFolder()

    private val motion = FakeMotionSensor()
    private val scanner = FakeCodeScanner()
    private var done = false

    @Test
    fun sumsAreDoneOnlyWhenEverySumIsRight() {
        start(MissionType.SUMS)

        answer(rightAnswer() + 1)
        compose.onNodeWithContentDescription("Not quite. Try again.").assertExists()
        compose.onNodeWithText("1 of 3").assertExists()

        repeat(2) { answer(rightAnswer()) }
        compose.onNodeWithText("3 of 3").assertExists()
        assertNotDone()

        answer(rightAnswer())
        assertFinishes()
    }

    @Test
    fun shakingToTheGoalFinishesAndSwitchesTheSensorOff() {
        start(MissionType.SHAKE)

        repeat(29) { motion.jolt(it) }
        compose.waitUntil { compose.onAllNodes(hasText("29 of 30")).fetchSemanticsNodes().isNotEmpty() }
        assertNotDone()

        motion.jolt(29)
        assertFinishes()
        assertFalse(motion.listening)
    }

    @Test
    fun withNoMotionSensorShakeCanSwitchToSums() {
        start(MissionType.SHAKE, motion = FakeMotionSensor(available = false))

        compose.onNodeWithText("Switch to sums instead").performScrollTo().performClick()

        compose.onNodeWithText("Wake-up sums").assertExists()
        assertNotDone()
    }

    @Test
    fun scanFindsOnlyTheSavedCodeAndThenTurnsTheCameraOff() {
        val settings = settings()
        runBlocking { settings.setKettleCode("kettle-7") }
        start(MissionType.SCAN, settings)

        scanner.codes.trySend("someone-elses-kettle")
        compose.waitUntil { compose.onAllNodes(hasText("Not your kettle code")).fetchSemanticsNodes().isNotEmpty() }
        assertNotDone()

        scanner.codes.trySend("kettle-7")
        assertFinishes()
        assertFalse(scanner.cameraOn)
    }

    @Test
    fun withTheCameraOffScanCanSwitchToSums() {
        scanner.cameraAllowed.value = false
        start(MissionType.SCAN)

        compose.onNodeWithText("Camera is off").assertExists()
        compose.onNodeWithText("Switch to sums instead").performScrollTo().performClick()

        compose.onNodeWithText("Wake-up sums").assertExists()
        assertNotDone()
    }

    @Test
    fun lettingGoOfPipEarlyStartsTheBreathAgain() {
        val time = TestTimeSource()
        val mission = BreatheMission(time)
        // Holding Pip redraws every frame, so frames are stepped by hand rather than run until the screen is still.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MorrowTheme(ThemeKind.NIGHT) {
                val state by mission.state.collectAsState()
                BreatheBody(state, mission)
            }
        }
        val pip = compose.onNodeWithContentDescription("Press and hold to breathe")

        pip.performTouchInput { down(center) }
        time += 2.seconds
        compose.mainClock.advanceTimeBy(100)
        pip.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(BreatheNote.TOO_QUICK, mission.state.value.note)
        assertEquals(0, mission.state.value.breaths)
        compose.onNodeWithText("Too quick. Hold for the whole breath, in and out.").assertExists()

        pip.performTouchInput { down(center) }
        time += BreatheMission.HOLD
        compose.mainClock.advanceTimeBy(100)
        pip.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, mission.state.value.breaths)
        compose.onNodeWithText("Lovely. Two more.").assertExists()
    }

    private fun start(type: MissionType, settings: MorrowSettings = settings(), motion: MotionSensor = this.motion) {
        compose.setContent { MissionRoute(type, MissionServices(settings, motion, scanner), onDone = { done = true }) }
        // The mission appears once its settings have loaded from disk.
        compose.waitUntil { compose.onAllNodes(hasText("WAKE-UP MISSION")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Pip's "We're up!" stays on screen for a moment, then the mission is done. */
    private fun assertFinishes() {
        compose.waitForIdle()
        assertFalse(done)
        compose.mainClock.advanceTimeBy(FINISH_PAUSE)
        compose.waitUntil { done }
    }

    /** Still not done, even after the moment a finished mission would wait. */
    private fun assertNotDone() {
        compose.mainClock.advanceTimeBy(FINISH_PAUSE)
        compose.waitForIdle()
        assertFalse(done)
    }

    private fun settings() = MorrowSettings(createSettingsStore(folder.newFolder().resolve("settings.preferences_pb").absolutePath))

    private fun rightAnswer(): Int {
        val question = compose.onNode(hasContentDescription("What is", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        val (a, sign, b) = question.split(" ")
        return when (sign) {
            "+" -> a.toInt() + b.toInt()
            "−" -> a.toInt() - b.toInt()
            else -> a.toInt() * b.toInt()
        }
    }

    private fun answer(value: Int) {
        value.toString().forEach { digit ->
            compose.onNode(hasText(digit.toString()) and hasClickAction()).performScrollTo().performClick()
        }
        compose.onNodeWithContentDescription("Check answer").performScrollTo().performClick()
    }
}

private const val FINISH_PAUSE = 1_500L

private class FakeMotionSensor(override val available: Boolean = true) : MotionSensor {
    private val readings = Channel<Acceleration>(Channel.UNLIMITED)
    var listening = false
        private set

    override fun accelerations(): Flow<Acceleration> =
        readings.receiveAsFlow().onStart { listening = true }.onCompletion { listening = false }

    /** A hard jolt, the [n]th in a steady rhythm a third of a second apart. */
    fun jolt(n: Int) {
        readings.trySend(Acceleration(x = 30f, y = 0f, z = 0f, timeNanos = n * 330_000_000L))
    }
}

private class FakeCodeScanner : CodeScanner {
    override val cameraAllowed = MutableStateFlow(true)
    val codes = Channel<String>(Channel.UNLIMITED)
    var cameraOn = false
        private set

    override fun requestCamera() {}

    override fun refresh() {}

    @Composable
    override fun Viewfinder(onCode: (String) -> Unit, modifier: Modifier) {
        DisposableEffect(Unit) {
            cameraOn = true
            onDispose { cameraOn = false }
        }
        LaunchedEffect(Unit) { for (code in codes) onCode(code) }
    }
}
