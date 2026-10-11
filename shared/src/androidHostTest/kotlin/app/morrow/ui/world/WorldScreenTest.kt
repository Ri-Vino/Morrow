package app.morrow.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.morrow.places.City
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorldScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val world = WorldFixtures()
    private var addCityTapped = 0
    private val newAlarms = mutableListOf<City?>()

    private fun show() {
        runBlocking {
            world.cities.ensureSeeded()
            world.cities.add(world.tokyo)
            world.cities.add(world.newYork)
        }
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                WorldRoute(world.cities, onAddCity = { addCityTapped++ }, onNewAlarm = { newAlarms += it }, ticks = world.ticks, phoneZone = world.phoneZone)
            }
        }
    }

    private fun saved() = runBlocking { world.cities.cities.first() }

    private fun order() = saved().map { it.city.name }

    private val londonCard = "London, home city, 07:40, Today · your time"
    private val tokyoCard = "Tokyo, 16:40, Today · +9 h"
    private val newYorkCard = "New York City, 02:40, Today · −5 h"

    @Test
    fun theSheetStartsANewAlarmOnTheCitysClock() {
        show()

        compose.onNodeWithContentDescription(newYorkCard).performScrollTo().performClick()
        compose.onNodeWithText("New alarm in New York City time").performClick()
        compose.onNodeWithContentDescription(londonCard).performClick()
        compose.onNodeWithText("New alarm").performClick()

        assertEquals(listOf(world.newYork, null), newAlarms)
    }

    @Test
    fun showsTheSavedCitiesInOrderWithTheirTimes() {
        show()

        compose.onNodeWithText("YOUR TIME 07:40 · LONDON").assertIsDisplayed()
        compose.onNodeWithText("World").assertIsDisplayed()
        val tops = listOf(londonCard, tokyoCard, newYorkCard).map { compose.onNodeWithContentDescription(it).getBoundsInRoot().top }
        assertTrue(tops[0] < tops[1] && tops[1] < tops[2], "Cards should run London, Tokyo, New York, but their tops are $tops")
        compose.onNodeWithText("16:40", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("02:40", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theAddButtonOpensAddACity() {
        show()

        compose.onNodeWithContentDescription("Add a city").performClick()

        assertEquals(1, addCityTapped)
    }

    @Test
    fun tappingACardOpensItsSheet() {
        show()

        compose.onNodeWithContentDescription(tokyoCard).performClick()

        compose.onNodeWithText("Tokyo, Japan").assertIsDisplayed()
        compose.onNodeWithText("Make home city").assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
        compose.onNodeWithText("This is your home city. Weather uses it when location is off.").assertDoesNotExist()
    }

    @Test
    fun theScrimClosesTheSheet() {
        show()
        compose.onNodeWithContentDescription(tokyoCard).performClick()

        // The sheet covers the middle of the scrim, so tap its top corner.
        compose.onNodeWithContentDescription("Close").performTouchInput { click(Offset(10f, 10f)) }

        compose.onNodeWithText("Make home city").assertDoesNotExist()
        assertEquals(listOf("London", "Tokyo", "New York City"), order())
    }

    @Test
    fun theHomeCityCanNeitherBeMadeHomeNorRemoved() {
        show()

        compose.onNodeWithContentDescription(londonCard).performClick()

        compose.onNodeWithText("England, United Kingdom").assertIsDisplayed()
        compose.onNodeWithText("This is your home city. Weather uses it when location is off.").assertIsDisplayed()
        compose.onNodeWithText("Make home city").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun makeHomeCityMovesTheChipAndTheHeader() {
        show()
        compose.onNodeWithContentDescription(tokyoCard).performClick()

        compose.onNodeWithText("Make home city").performClick()

        compose.waitForIdle()
        assertEquals("Tokyo", runBlocking { world.cities.home() }?.city?.name)
        compose.onNodeWithText("Make home city").assertDoesNotExist()
        compose.onNodeWithText("YOUR TIME 07:40 · TOKYO").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tokyo, home city, 16:40, Today · +9 h").assertExists()
        compose.onNodeWithContentDescription("London, 07:40, Today · your time").assertExists()
    }

    @Test
    fun removeTakesTheCityOffTheTab() {
        show()
        compose.onNodeWithContentDescription(tokyoCard).performClick()

        compose.onNodeWithText("Remove").performClick()

        compose.waitForIdle()
        assertEquals(listOf("London", "New York City"), order())
        compose.onNodeWithContentDescription(tokyoCard).assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun talkBackCanMoveACardUpAndDown() {
        show()

        moveAction(newYorkCard, "Move up")
        assertEquals(listOf("London", "New York City", "Tokyo"), order())
        moveAction(newYorkCard, "Move up")
        assertEquals(listOf("New York City", "London", "Tokyo"), order())
        moveAction(newYorkCard, "Move down")
        moveAction(newYorkCard, "Move down")
        assertEquals(listOf("London", "Tokyo", "New York City"), order())

        assertEquals(listOf("Move down"), actionsOf(londonCard))
        assertEquals(listOf("Move up", "Move down"), actionsOf(tokyoCard))
        assertEquals(listOf("Move up"), actionsOf(newYorkCard))
    }

    @Test
    fun theCardsFollowTheNewOrderOnScreen() {
        show()

        moveAction(newYorkCard, "Move up")

        val tops = listOf(londonCard, newYorkCard, tokyoCard).map { compose.onNodeWithContentDescription(it).getBoundsInRoot().top }
        assertTrue(tops[0] < tops[1] && tops[1] < tops[2], "Cards should run London, New York, Tokyo, but their tops are $tops")
    }

    @Test
    fun holdingACardAndDraggingItDownMovesIt() {
        show()
        val height = compose.onNodeWithContentDescription(tokyoCard).getBoundsInRoot().let { it.bottom - it.top }

        compose.onNodeWithContentDescription(tokyoCard).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 10f))
            moveBy(Offset(0f, (height + 12.dp).toPx()))
            up()
        }

        compose.waitForIdle()
        assertEquals(listOf("London", "New York City", "Tokyo"), order())
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show()
        assertAllLabelled()

        compose.onNodeWithContentDescription(tokyoCard).performClick()
        assertAllLabelled()
    }

    private fun assertAllLabelled() {
        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }

    private fun card(description: String) =
        compose.onNode(hasContentDescriptionExactly(description) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))

    private fun actionsOf(description: String) = card(description).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun moveAction(description: String, label: String) {
        val action = card(description).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }
}
