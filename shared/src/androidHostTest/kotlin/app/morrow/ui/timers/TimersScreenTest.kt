package app.morrow.ui.timers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import app.morrow.alarms.FakeClock
import app.morrow.data.FakeTimerDao
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.FakeTimerNotifier
import app.morrow.platform.FakeTimerScheduler
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.timers.Timer
import app.morrow.timers.TimerCoordinator
import app.morrow.timers.TimerRepository
import app.morrow.ui.NoMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val noon = Instant.parse("2026-10-12T12:00:00Z")

@RunWith(RobolectricTestRunner::class)
class TimersScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val dao = FakeTimerDao()
    private val repository = TimerRepository(dao)
    private val scheduler = FakeTimerScheduler()
    private val notifier = FakeTimerNotifier()
    private val clock = FakeClock(noon)
    private val timers = TimerCoordinator(repository, scheduler, notifier, clock)
    private val ticks = MutableStateFlow(noon)
    private val requested = mutableListOf<AlarmPermission>()
    private val permissions = object : PermissionGate {
        override val asked = AndroidPermissions
        override val missing: StateFlow<List<AlarmPermission>> = MutableStateFlow(emptyList())
        override fun refresh() = Unit
        override fun request(permission: AlarmPermission) {
            requested += permission
        }
    }

    @Composable
    private fun TimersTab(fontScale: Float = 1f) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            MorrowTheme(ThemeKind.MORNING) {
                TimersRoute(timers, permissions, ticks = ticks, zone = TimeZone.UTC)
            }
        }
    }

    private fun show(fontScale: Float = 1f) {
        compose.setContent { TimersTab(fontScale) }
        compose.waitForIdle()
    }

    private fun stored(): List<Timer> = runBlocking { repository.getAll() }

    private fun start(duration: Int, note: String = "") = runBlocking { timers.start(duration.minutes, note) }

    private fun key(label: String) = compose.onNode(hasText(label) and hasClickAction())

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private fun tapDescribed(description: String) = compose.onNodeWithContentDescription(description).tap()

    /** Types [digits] on the keypad of the sheet that tapping the dial's time opens, as hhmmss filled from the right. */
    private fun typeLength(digits: String) {
        compose.onNode(hasClickLabel("Type the length")).tap()
        digits.forEach { key(it.toString()).performClick() }
        key("Set length").performClick()
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun waitForTimers(count: Int) = compose.waitUntil(timeoutMillis = 5_000) { stored().size == count }

    @Test
    fun theLengthCanBeTypedAndTheDialSaysIt() {
        show()
        compose.onNodeWithContentDescription("No time set yet").assertIsDisplayed()

        typeLength("1500")
        compose.onNodeWithContentDescription("Timer for 15 min").assertExists()

        // The sheet opens on the dial's length, and the first key replaces it.
        typeLength("10203")
        compose.onNodeWithContentDescription("Timer for 1 h 2 min").assertExists()
    }

    @Test
    fun minutesOverFiftyNineAddUp() {
        show()

        typeLength("7500")

        compose.onNodeWithContentDescription("Timer for 1 h 15 min").assertExists()
    }

    @Test
    fun theKeypadShowsTheLengthAsItsTypedAndDeletes() {
        show()
        compose.onNode(hasClickLabel("Type the length")).tap()

        listOf("1", "5", "00").forEach { key(it).performClick() }
        compose.onNodeWithContentDescription("Timer for 15 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Delete digit").performClick()
        compose.onNodeWithContentDescription("Timer for 2 min", useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun talkBackCanAddAndTakeOffAMinute() {
        show()

        compose.onNodeWithContentDescription("No time set yet").performCustomAccessibilityActionWithLabel("One minute more")
        compose.onNodeWithContentDescription("Timer for 1 min").performCustomAccessibilityActionWithLabel("One minute more")
        compose.onNodeWithContentDescription("Timer for 2 min").performCustomAccessibilityActionWithLabel("One minute less")
        compose.onNodeWithContentDescription("Timer for 1 min").assertExists()
    }

    @Test
    fun windingPipAQuarterTurnSetsFifteenMinutes() {
        show()

        compose.onNodeWithContentDescription("No time set yet").performTouchInput {
            down(Offset(width / 2f, height * 0.09f))
            moveTo(Offset(width * 0.8f, height * 0.2f))
            moveTo(Offset(width * 0.91f, height / 2f))
            up()
        }

        compose.onNodeWithContentDescription("Timer for 15 min").assertExists()
    }

    @Test
    fun aQuickChipSetsThatLength() {
        show()

        key("25 min").tap()
        compose.onNodeWithContentDescription("Timer for 25 min").assertExists()

        key("1 hour").tap()
        compose.onNodeWithContentDescription("Timer for 1 h").assertExists()
    }

    @Test
    fun startingSetsATimerForTheTypedLengthWithItsNote() {
        show()
        typeLength("1000")
        compose.onNodeWithContentDescription(NOTE_LABEL).tap().performTextInput("Take the pasta off the hob")

        key("Start timer").tap()

        waitForTimers(1)
        val timer = stored().single()
        assertEquals(10.minutes, timer.duration)
        assertEquals("Take the pasta off the hob", timer.note)
        assertEquals(mapOf(timer.id to noon + 10.minutes), scheduler.scheduled)
    }

    @Test
    fun onceStartedTheSetterGivesWayToTheTimersCardAndTheNewTimerButton() {
        show()
        typeLength("1000")

        key("Start timer").tap()

        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("10:00")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New timer").assertIsDisplayed()
        compose.onNodeWithContentDescription("No time set yet").assertDoesNotExist()
        compose.onNodeWithText("1 RUNNING").assertExists()
    }

    @Test
    fun startDoesNothingUntilThereIsSomeTime() {
        show()

        key("Start timer").assertIsNotEnabled()
        key("Start timer").tap()
        key("Start timer").tap()
        compose.waitForIdle()

        assertEquals(emptyList(), stored())
    }

    @Test
    fun theNoteStopsAtSixtyCharacters() {
        show()

        compose.onNodeWithContentDescription(NOTE_LABEL).tap().performTextInput("a".repeat(70))

        compose.onNodeWithContentDescription(NOTE_LABEL).assertTextEquals("a".repeat(60))
    }

    @Test
    fun withoutExactAlarmsStartOpensThePermissionAndKeepsWhatWasTyped() {
        scheduler.allowed = false
        show()
        typeLength("1000")

        key("Start timer").tap()

        compose.waitUntil(timeoutMillis = 5_000) { requested.isNotEmpty() }
        assertEquals(listOf(AlarmPermission.EXACT_ALARMS), requested)
        assertEquals(emptyList(), stored())
        compose.onNodeWithContentDescription("Timer for 10 min").assertExists()
    }

    @Test
    fun theDraftSurvivesRotation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TimersTab() }
        typeLength("1000")
        compose.onNodeWithContentDescription(NOTE_LABEL).tap().performTextInput("Tea")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Timer for 10 min").assertExists()
        compose.onNodeWithContentDescription(NOTE_LABEL).assertTextEquals("Tea")
    }

    @Test
    fun aRunningTimerShowsItsTimeNoteAndTotal() {
        start(10, "Pasta")
        show()

        compose.onNodeWithText("10:00").assertIsDisplayed()
        compose.onNodeWithText("Pasta · 10 min").assertIsDisplayed()
        compose.onNodeWithText("Timers").assertIsDisplayed()
        compose.onNodeWithText("New timer").assertIsDisplayed()
        compose.onNodeWithContentDescription("No time set yet").assertDoesNotExist()
    }

    @Test
    fun theCountdownFollowsTheClock() {
        start(10, "Pasta")
        show()

        ticks.value = noon + 2.minutes
        compose.waitForIdle()

        compose.onNodeWithText("08:00").assertExists()
    }

    @Test
    fun plusOneMinuteAddsAMinuteToThatTimer() {
        start(10, "Pasta")
        show()

        tapDescribed("Add a minute to Pasta")

        compose.waitUntil(timeoutMillis = 5_000) { stored().single().duration == 11.minutes }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("11:00")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun pauseHoldsTheTimerAndResumeRunsItOn() {
        val id = assertNotNull(start(10, "Pasta"))
        show()

        tapDescribed("Pause Pasta")
        compose.waitUntil(timeoutMillis = 5_000) { stored().single().endsAt == null }
        assertEquals(emptyMap(), scheduler.scheduled)
        compose.onNodeWithContentDescription("Resume Pasta").assertExists()
        compose.onNodeWithText("Paused · Pasta · 10 min").assertExists()
        compose.onNodeWithText("1 PAUSED").assertExists()

        tapDescribed("Resume Pasta")
        compose.waitUntil(timeoutMillis = 5_000) { stored().single().endsAt != null }
        assertEquals(mapOf(id to noon + 10.minutes), scheduler.scheduled)
        compose.onNodeWithContentDescription("Pause Pasta").assertExists()
    }

    @Test
    fun resumeWithoutExactAlarmsOpensThePermissionAndLeavesTheTimerPaused() {
        start(10, "Pasta")
        show()
        tapDescribed("Pause Pasta")
        compose.waitUntil(timeoutMillis = 5_000) { stored().single().endsAt == null }
        scheduler.allowed = false

        tapDescribed("Resume Pasta")

        compose.waitUntil(timeoutMillis = 5_000) { requested.isNotEmpty() }
        assertEquals(listOf(AlarmPermission.EXACT_ALARMS), requested)
        assertNull(stored().single().endsAt)
    }

    @Test
    fun deleteStopsTheTimerAndBringsTheSetterBack() {
        start(10, "Pasta")
        show()

        tapDescribed("Delete Pasta")

        waitForTimers(0)
        compose.onNodeWithContentDescription("No time set yet").assertIsDisplayed()
        compose.onNodeWithText("New timer").assertDoesNotExist()
        compose.onNodeWithText("PIP READS YOUR NOTE WHEN ONE ENDS").assertExists()
    }

    @Test
    fun twoTimersRunAtOnceAndEachCardControlsItsOwnTimer() {
        val pasta = assertNotNull(start(10, "Pasta"))
        val eggs = assertNotNull(start(5, "Eggs"))
        show()
        compose.onNodeWithText("2 RUNNING").assertExists()

        tapDescribed("Pause Eggs")
        compose.waitUntil(timeoutMillis = 5_000) { stored().first { it.id == eggs }.endsAt == null }

        assertNotNull(stored().first { it.id == pasta }.endsAt)
        tapDescribed("Add a minute to Pasta")
        compose.waitUntil(timeoutMillis = 5_000) { stored().first { it.id == pasta }.duration == 11.minutes }
        assertEquals(5.minutes, stored().first { it.id == eggs }.duration)

        tapDescribed("Delete Eggs")
        waitForTimers(1)
        compose.onNodeWithContentDescription("Pause Pasta").assertExists()
    }

    @Test
    fun newTimerOpensTheSetterOverTheRunningTimersAndCancelPutsItAway() {
        start(10, "Pasta")
        show()
        compose.onNodeWithContentDescription("No time set yet").assertDoesNotExist()

        compose.onNodeWithText("New timer").performClick()
        compose.onNodeWithContentDescription("No time set yet").assertExists()
        compose.onNodeWithText("New timer").assertDoesNotExist()
        compose.onNodeWithText("Pasta · 10 min").assertExists()

        typeLength("500")
        key("Cancel").tap()

        compose.onNodeWithContentDescription("No time set yet").assertDoesNotExist()
        compose.onNodeWithText("New timer").assertIsDisplayed()
        // What was typed went with it.
        compose.onNodeWithText("New timer").performClick()
        compose.onNodeWithContentDescription("No time set yet").assertExists()
    }

    @Test
    fun aSecondTimerCanBeStartedFromNewTimer() {
        start(10, "Pasta")
        show()

        compose.onNodeWithText("New timer").performClick()
        key("5 min").tap()
        key("Start timer").tap()

        waitForTimers(2)
        assertEquals(listOf(10.minutes, 5.minutes), stored().map { it.duration })
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("2 RUNNING")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New timer").assertIsDisplayed()
    }

    @Test
    fun aTimerThatHasEndedSaysSoAndOffersNoPause() {
        start(10, "Pasta")
        show()

        clock.now = noon + 10.minutes
        runBlocking { timers.ringEnded(behindAlarm = false) }
        ticks.value = clock.now
        compose.waitForIdle()

        compose.onNodeWithText("Time’s up").assertIsDisplayed()
        compose.onNodeWithText("Ringing now · Pasta · 10 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause Pasta").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add a minute to Pasta").assertExists()
        compose.onNodeWithContentDescription("Delete Pasta").assertExists()
    }

    @Test
    fun aTimerWaitingBehindAnAlarmSaysItRingsAfterwards() {
        start(5, "Eggs")
        show()

        clock.now = noon + 5.minutes
        runBlocking { timers.ringEnded(behindAlarm = true) }
        ticks.value = clock.now
        compose.waitForIdle()

        compose.onNodeWithText("Rings after your alarm · Eggs · 5 min").assertIsDisplayed()
        compose.onNodeWithText("Time’s up").assertIsDisplayed()
    }

    @Test
    fun atDoubleTextSizeNothingIsCutOffAndEveryControlCanBeReached() {
        show(fontScale = 2f)

        val screen = compose.onRoot().getBoundsInRoot()
        val dial = compose.onNodeWithContentDescription("No time set yet")
        dial.assertIsDisplayed()
        assertTrue(dial.getBoundsInRoot().right <= screen.right, "the dial runs off the side of the screen")
        listOf("1 min", "1 hour", "Start timer").forEach { key(it).performScrollTo().assertIsDisplayed() }
        compose.onNode(hasClickLabel("Type the length")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(NOTE_LABEL).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun atDoubleTextSizeACardKeepsItsButtonsOnScreen() {
        start(10, "Take the pasta off the hob")
        show(fontScale = 2f)

        compose.onNodeWithText("10:00").performScrollTo().assertIsDisplayed()
        listOf("Pause Take the pasta off the hob", "Add a minute to Take the pasta off the hob", "Delete Take the pasta off the hob").forEach {
            compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
        }
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue(compose.onNodeWithContentDescription("Delete Take the pasta off the hob").getBoundsInRoot().right <= screen.right)
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    private fun unlabelled() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
        val config = node.config
        config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
    }.map { it.config.toString() }

    @Test
    fun everyControlOfTheSetterHasALabel() {
        show()

        assertEquals(emptyList(), unlabelled())
        // The time to type, six chips, the note field and Start.
        val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue(controls >= 9, "found only $controls controls")
    }

    @Test
    fun everyControlOnTheCardsAndTheSetterOverThemHasALabel() {
        start(10, "Pasta")
        start(5)
        show()
        assertEquals(emptyList(), unlabelled())

        compose.onNodeWithText("New timer").performClick()
        assertEquals(emptyList(), unlabelled())
    }

    @Test
    fun theRingingStateHasLabelledControlsToo() {
        start(10, "Pasta")
        show()
        clock.now = noon + 10.minutes
        runBlocking { timers.ringEnded(behindAlarm = false) }
        ticks.value = clock.now
        compose.waitForIdle()

        assertEquals(emptyList(), unlabelled())
    }
}
