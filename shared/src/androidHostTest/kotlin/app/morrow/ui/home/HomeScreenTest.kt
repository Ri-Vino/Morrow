package app.morrow.ui.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import app.morrow.weather.Sky
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val london = TimeZone.of("Europe/London")
    private val now = LocalDateTime(2026, 10, 6, 7, 40).toInstant(london)
    private var worldOpened = 0
    private val deleted = mutableListOf<Long>()
    private val toggled = mutableListOf<Pair<Long, Boolean>>()
    private var undone = 0
    private var undoClosed = 0
    private var newAlarms = 0
    private var newTemporary = 0
    private val quick = mutableListOf<Pair<Int, Boolean>>()

    private val rain = HomeWeather(Sky.RAIN, night = false, text = "12° · Rain in London", label = "12° and rain in London. Open World")
    private val work = Alarm(7, 7, 30, "Work", emptySet(), listOf(GameStep(MissionType.SUMS)), enabled = true)

    private fun show(weather: HomeWeather?, alarms: List<Alarm> = emptyList(), undo: String? = null) {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                HomeScreen(
                    model = homeModel(alarms, now, london, emptyList(), weather = weather).copy(undo = undo),
                    theme = ThemeKind.MORNING,
                    minuteOfDay = 7 * 60 + 40f,
                    actions = HomeActions(
                        onToggle = { id, on -> toggled += id to on },
                        onOpenAlarm = {},
                        onNewAlarm = { newAlarms++ },
                        onTrySunrise = {},
                        onSettings = {},
                        onFixPermissions = {},
                        onOpenWorld = { worldOpened++ },
                        onDelete = { deleted += it },
                        onUndo = { undone++ },
                        onDismissUndo = { undoClosed++ },
                        onNewTemporary = { newTemporary++ },
                        onQuickAlarm = { minutes, nap -> quick += minutes to nap },
                    ),
                )
            }
        }
    }

    @Test
    fun theWeatherLineSitsUnderTheGreeting() {
        show(rain)

        compose.onNodeWithText("12° · Rain in London", useUnmergedTree = true).assertIsDisplayed()
        val greeting = compose.onNodeWithText("Good morning").getBoundsInRoot()
        val line = compose.onNodeWithContentDescription(rain.label).getBoundsInRoot()
        assertTrue(line.top >= greeting.bottom, "The weather line should be under the greeting")
    }

    @Test
    fun theWeatherLineIsAButtonThatOpensWorld() {
        show(rain)

        compose.onNodeWithContentDescription("12° and rain in London. Open World").assertHasClickAction().performClick()

        assertEquals(1, worldOpened)
    }

    @Test
    fun theWeatherLineIsBigEnoughToTap() {
        show(rain)

        val line = compose.onNodeWithContentDescription(rain.label).getBoundsInRoot()
        assertTrue(line.bottom - line.top >= 44.dp, "The weather line is only ${line.bottom - line.top} tall")
    }

    @Test
    fun withoutWeatherThereIsNoLine() {
        show(null)

        compose.onNodeWithText("Good morning").assertIsDisplayed()
        compose.onAllNodes(hasText("°", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun swipingARowLeftDeletesIt() {
        show(null, alarms = listOf(work))

        compose.onNodeWithText("07:30").performScrollTo()
            // From the time to well past the row's left edge: the time only covers part of the row.
            .performTouchInput { swipeLeft(startX = right, endX = -width.toFloat()) }
        compose.waitForIdle()

        assertEquals(listOf(7L), deleted)
    }

    @Test
    fun aShortSwipeLeavesTheRow() {
        show(null, alarms = listOf(work))

        compose.onNodeWithText("07:30").performScrollTo()
            .performTouchInput { swipeLeft(startX = centerX, endX = centerX - width * 0.1f) }
        compose.waitForIdle()

        assertTrue(deleted.isEmpty())
    }

    @Test
    fun screenReadersCanDeleteARowWithoutSwiping() {
        show(null, alarms = listOf(work))

        val actions = compose.onNodeWithText("07:30").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.single { it.label == "Delete" }.action()

        assertEquals(listOf(7L), deleted)
    }

    @Test
    fun switchingAnOrdinaryAlarmOffJustTurnsItOff() {
        show(null, alarms = listOf(work))

        compose.onNode(isToggleable()).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(7L to false), toggled)
        compose.onNodeWithText("Remove alarm?").assertDoesNotExist()
    }

    @Test
    fun switchingATemporaryAlarmOffAsksBeforeRemovingIt() {
        show(null, alarms = listOf(work.copy(label = "Nap", temporary = true)))

        compose.onNode(isToggleable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Remove alarm?").assertIsDisplayed()
        // Nothing has happened to it yet.
        assertTrue(toggled.isEmpty() && deleted.isEmpty())

        compose.onNodeWithText("Remove alarm").performClick()

        assertEquals(listOf(7L), deleted)
        assertTrue(toggled.isEmpty(), "A removed alarm isn't switched off first")
    }

    @Test
    fun keepingATemporaryAlarmOnLeavesItAlone() {
        show(null, alarms = listOf(work.copy(label = "Nap", temporary = true)))

        compose.onNode(isToggleable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Keep it on").performClick()

        compose.onNodeWithText("Remove alarm?").assertDoesNotExist()
        assertTrue(toggled.isEmpty() && deleted.isEmpty())
    }

    @Test
    fun switchingATemporaryAlarmBackOnDoesntAsk() {
        show(null, alarms = listOf(work.copy(label = "Nap", temporary = true, enabled = false)))

        compose.onNode(isToggleable()).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(7L to true), toggled)
        compose.onNodeWithText("Remove alarm?").assertDoesNotExist()
    }

    @Test
    fun theUndoLinePutsTheAlarmBack() {
        show(null, undo = "07:30 alarm deleted")

        compose.onNodeWithText("07:30 alarm deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()

        assertEquals(1, undone)
    }

    @Test
    fun theUndoLineCanBeClosed() {
        show(null, undo = "07:30 alarm deleted")

        compose.onNodeWithContentDescription("Close").performClick()

        assertEquals(1, undoClosed)
        assertEquals(0, undone)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun onAWideScreenTheAlarmsStartOnScreen() {
        show(null, alarms = listOf(work))

        val title = compose.onNodeWithText("Alarms").getBoundsInRoot()
        assertTrue(title.top < 360.dp, "The alarms start at ${title.top}, below the screen")
    }

    @Test
    fun tappingTheSunMakesANormalAlarm() {
        show(null, listOf(work))

        compose.onNodeWithContentDescription("New alarm").performClick()

        assertEquals(1, newAlarms)
        compose.onNodeWithText("Temporary").assertDoesNotExist()
    }

    @Test
    fun holdingTheSunOffersTheOtherKinds() {
        show(null, listOf(work))

        compose.onNodeWithContentDescription("New alarm").performTouchInput { longClick() }
        compose.onNodeWithText("Temporary").assertIsDisplayed()
        compose.onNodeWithText("Nap").assertIsDisplayed()
        compose.onNodeWithText("In X min").performClick()

        compose.onNodeWithText("Ring in").assertIsDisplayed()
        compose.onNodeWithText("15").performClick()
        compose.onNodeWithText("Ring at 07:55").performScrollTo().performClick()
        assertEquals(listOf(15 to false), quick)
        assertEquals(0, newAlarms)
    }

    @Test
    fun aNapStartsAtTwentyMinutes() {
        show(null, listOf(work))

        compose.onNodeWithContentDescription("New alarm").performTouchInput { longClick() }
        compose.onNodeWithText("Nap").performClick()
        compose.onNodeWithText("Nap until 08:00").performScrollTo().performClick()

        assertEquals(listOf(20 to true), quick)
    }

    @Test
    fun temporaryOpensEditAndTheChoicesAreThereForScreenReaders() {
        show(null, listOf(work))

        val actions = compose.onNodeWithContentDescription("New alarm").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Temporary alarm", "Nap", "Alarm in a few minutes"), actions.map { it.label })
        actions.first().action()
        compose.waitForIdle()

        assertEquals(1, newTemporary)
    }
}
