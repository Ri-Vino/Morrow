package app.morrow.ui.mission

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.morrow.data.MorrowSettings
import app.morrow.data.createSettingsStore
import app.morrow.domain.Difficulty
import app.morrow.domain.GameStep
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The alarm stops when the sequence calls done, so it must call it once the last game is finished and not before. */
@RunWith(RobolectricTestRunner::class)
class SequenceRouteTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    @get:Rule
    val folder = TemporaryFolder()

    private val motion = FakeMotionSensor()
    private val scanner = FakeCodeScanner()
    private var done = false

    @Test
    fun anAlarmWithNoGamesIsDoneAtOnce() {
        compose.setContent {
            SequenceRoute(emptyList(), null, MissionServices(settings(), motion, scanner), onDone = { done = true })
        }
        assertFinishes()
        compose.onAllNodes(hasText("WAKE-UP MISSION")).assertCountEquals(0)
    }

    @Test
    fun sumsAreDoneOnlyWhenEverySumIsRight() {
        start(GameStep(MissionType.SUMS, 3))

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
        start(GameStep(MissionType.SHAKE, 3))

        repeat(29) { motion.jolt(it) }
        compose.waitUntil { compose.onAllNodes(hasText("29 of 30")).fetchSemanticsNodes().isNotEmpty() }
        assertNotDone()

        motion.jolt(29)
        assertFinishes()
        assertFalse(motion.listening)
    }

    @Test
    fun withNoMotionSensorShakeCanSwitchToSums() {
        start(GameStep(MissionType.SHAKE, 3), motion = FakeMotionSensor(available = false))

        compose.onNodeWithText("Switch to sums instead").performScrollTo().performClick()

        compose.onNodeWithText("Wake-up sums").assertExists()
        assertNotDone()
    }

    @Test
    fun scanFindsOnlyTheSavedCodeAndThenTurnsTheCameraOff() {
        val settings = settings()
        runBlocking { settings.setKettleCode("kettle-7") }
        start(GameStep(MissionType.SCAN, 1), settings = settings)

        scanner.codes.trySend("someone-elses-kettle")
        compose.waitUntil { compose.onAllNodes(hasText("Not your code")).fetchSemanticsNodes().isNotEmpty() }
        assertNotDone()

        scanner.codes.trySend("kettle-7")
        assertFinishes()
        assertFalse(scanner.cameraOn)
    }

    @Test
    fun withTheCameraOffScanCanSwitchToSums() {
        scanner.cameraAllowed.value = false
        start(GameStep(MissionType.SCAN, 1))

        compose.onNodeWithText("Camera is off").assertExists()
        compose.onNodeWithText("Switch to sums instead").performScrollTo().performClick()

        compose.onNodeWithText("Wake-up sums").assertExists()
        assertNotDone()
    }

    @Test
    fun lettingGoOfPipEarlyStartsTheBreathAgain() {
        val time = TestTimeSource()
        val mission = BreatheMission(time = time)
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

    @Test
    fun twoGamesAreDoneOnlyAfterTheSecond() {
        start(GameStep(MissionType.SUMS, 1), GameStep(MissionType.SHAKE, 1))
        compose.onNodeWithContentDescription("Game 1 of 2, Sums, now").assertExists()
        compose.onNodeWithContentDescription("Game 2 of 2, Shake, next").assertExists()

        answer(rightAnswer())
        compose.onNodeWithText("Nice one! Next up: shake it off.").assertExists()
        compose.onNodeWithContentDescription("Game 1 of 2, Sums, done").assertExists()
        assertNotDone()

        compose.onNodeWithText("Shake it off").assertExists()
        compose.onNodeWithContentDescription("Game 2 of 2, Shake, now").assertExists()
        compose.onNodeWithText("0 of 10").assertExists()
        repeat(9) { motion.jolt(it) }
        compose.waitUntil { compose.onAllNodes(hasText("9 of 10")).fetchSemanticsNodes().isNotEmpty() }
        assertNotDone()

        motion.jolt(9)
        assertFinishes()
    }

    @Test
    fun switchingToSumsReplacesOnlyTheGameThatCantBePlayed() {
        scanner.cameraAllowed.value = false
        start(GameStep(MissionType.SCAN, 1), GameStep(MissionType.SUMS, 1))

        compose.onNodeWithText("Switch to sums instead").performScrollTo().performClick()
        compose.onNodeWithText("Wake-up sums").assertExists()
        compose.onNodeWithContentDescription("Game 1 of 2, Sums, now").assertExists()
        compose.onNodeWithText("1 of 3").assertExists()
        repeat(3) { answer(rightAnswer()) }
        assertNotDone()

        compose.onNodeWithContentDescription("Game 2 of 2, Sums, now").assertExists()
        compose.onNodeWithText("1 of 1").assertExists()
        answer(rightAnswer())
        assertFinishes()
    }

    @Test
    fun typeIsDoneOnlyOnceTheWholeSentenceIsTyped() {
        start(GameStep(MissionType.SUMS, 1), GameStep(MissionType.TYPE, 1))

        answer(rightAnswer())
        compose.onNodeWithText("Nice one! Next up: type it out.").assertExists()
        assertNotDone()

        compose.onNodeWithText("Type it out").assertExists()
        compose.onNodeWithContentDescription("Game 2 of 2, Type, now").assertExists()
        val sentence = shownSentence()
        val field = compose.onNodeWithContentDescription("Your sentence")
        field.typeWords(sentence.dropLast(4))
        assertNotDone()

        field.performTextInput(sentence.takeLast(4))
        assertFinishes()
    }

    @Test
    fun unscrambleIsDoneOnlyOnceEveryWordIsTappedInOrder() {
        start(GameStep(MissionType.UNSCRAMBLE, 1))
        compose.onNodeWithText("Unscramble").assertExists()

        // The order is hidden, so try the chips one by one: a wrong tap places nothing and never finishes the game.
        val total = soFar().let { chipCount() }
        var wrongTaps = 0
        repeat(total) {
            val before = soFar()
            var chip = 0
            while (soFar() == before) {
                compose.onAllNodes(hasClickAction())[chip++].performScrollTo().performClick()
                compose.waitForIdle()
                if (soFar() == before) {
                    wrongTaps++
                    assertNotDone()
                }
            }
        }

        assertTrue(wrongTaps > 0)
        assertFinishes()
    }

    @Test
    fun theAlarmSoundsQuieterUnderTheGamesAndMutesForAMinute() {
        val levels = mutableListOf<Float>()
        start(GameStep(MissionType.SUMS, 2), volume = { levels += it })
        compose.waitForIdle()
        assertEquals(listOf(GAME_LEVEL), levels)

        compose.onNode(hasClickLabel("Mute the alarm for a minute")).performClick()
        compose.waitForIdle()
        assertEquals(0f, levels.last())
        compose.onNodeWithContentDescription("Alarm muted, back in 60 seconds").assertExists()

        compose.onNode(hasClickLabel("Let the alarm sound")).performClick()
        compose.waitForIdle()
        assertEquals(GAME_LEVEL, levels.last())
    }

    @Test
    fun withoutAVolumeThereIsNoMuteButton() {
        start(GameStep(MissionType.SUMS, 2))
        compose.onNode(hasClickLabel("Mute the alarm for a minute")).assertDoesNotExist()
    }

    @Test
    fun aSingleGameHasNoChips() {
        start(GameStep(MissionType.SUMS, 2))
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithContentDescription("Game 1 of 1", substring = true).assertDoesNotExist()
    }

    private fun start(
        vararg games: GameStep,
        settings: MorrowSettings = settings(),
        motion: MotionSensor = this.motion,
        difficulty: Difficulty? = null,
        volume: GameVolume? = null,
    ) {
        compose.setContent {
            SequenceRoute(games.toList(), difficulty, MissionServices(settings, motion, scanner), volume, onDone = { done = true })
        }
        // The mission appears once its settings have loaded from disk.
        compose.waitUntil { compose.onAllNodes(hasText("WAKE-UP MISSION")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /** The last game done, the mission is done straight away, with no pause on the clock. */
    private fun assertFinishes() {
        compose.waitForIdle()
        assertTrue(done)
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

    /** The sentence Type asks for, shown just under its "TYPE THIS" label. */
    private fun shownSentence(): String = compose.onNodeWithText("TYPE THIS").onParent().onChildren()[1]
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun soFar(): String = compose.onNodeWithContentDescription("So far", substring = true)
        .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    private fun chipCount(): Int = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

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
