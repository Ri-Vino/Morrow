package app.morrow.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
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
import app.morrow.weather.Units
import app.morrow.weather.WeatherState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
class WorldScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val world = WorldFixtures()
    private var addCityTapped = 0
    private val newAlarms = mutableListOf<City?>()

    private fun show(weather: WeatherState = WeatherState.Off, units: Units = Units.CELSIUS, unitsFrom: Flow<Units>? = null) {
        runBlocking {
            world.cities.ensureSeeded()
            world.cities.add(world.tokyo)
            world.cities.add(world.newYork)
        }
        val forecasts = flowOf(weather)
        val inUnits = unitsFrom ?: flowOf(units)
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                WorldRoute(
                    world.cities,
                    onAddCity = { addCityTapped++ },
                    onNewAlarm = { newAlarms += it },
                    weather = forecasts,
                    units = inUnits,
                    ticks = world.ticks,
                    phoneZone = world.phoneZone,
                )
            }
        }
    }

    private fun assertNoTemperatures() {
        compose.onAllNodes(hasText("°", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("°", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun withWeatherOffCardsShowTheirTimesOnly() {
        show(WeatherState.Off)

        listOf("07:40", "16:40", "02:40").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        listOf(londonCard, tokyoCard, newYorkCard).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        assertNoTemperatures()
        compose.onNodeWithText("Weather is off, so cards show the time of day only.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withWeatherOffTheSheetHasNoHoursOrDay() {
        show(WeatherState.Off)

        compose.onNodeWithContentDescription(londonCard).performClick()

        compose.onNodeWithText("England, United Kingdom").assertIsDisplayed()
        assertNoTemperatures()
    }

    @Test
    fun untilTheUnitsAreKnownNoTemperatureShows() {
        // Settings are still loading: a °F phone mustn't see °C first.
        val units = MutableSharedFlow<Units>(replay = 1)
        show(world.weather(), unitsFrom = units)

        compose.waitForIdle()
        assertNoTemperatures()

        runBlocking { units.emit(Units.FAHRENHEIT) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("°", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun weatherOverADayOldLeavesTheTimesOnly() {
        show(world.weather(age = 25.hours))

        listOf(londonCard, tokyoCard, newYorkCard).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        assertNoTemperatures()
        compose.onNodeWithText("No recent weather, so cards show the time of day only.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withWeatherOnCardsShowTheTemperatureConditionAndHighAndLow() {
        show(world.weather())

        compose.onNodeWithText("12° Rain", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("H 14°  L 8°", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("21° Partly cloudy", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("$londonCard, 12° and rain").assertExists()
        compose.onNodeWithContentDescription("$newYorkCard, 11° and clear").assertExists()
        compose.onNodeWithText("Weather by Open-Meteo.com · updated 5 min ago").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cardsFollowTheChosenUnits() {
        show(world.weather(), Units.FAHRENHEIT)

        compose.onNodeWithText("54° Rain", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("H 57°  L 46°", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theSheetAddsTheNextSixHoursAndTheDay() {
        show(world.weather())

        compose.onNodeWithContentDescription("$londonCard, 12° and rain").performClick()

        compose.onNodeWithText("Today · your time · 12° Rain").assertIsDisplayed()
        compose.onNodeWithContentDescription("08:00, 13°, rain").assertIsDisplayed()
        compose.onNodeWithContentDescription("13:00, 18°, rain").assertIsDisplayed()
        compose.onNodeWithContentDescription("14:00, 19°, rain").assertDoesNotExist()
        compose.onNodeWithText("High 14° · Low 8° · 80% chance of rain").assertIsDisplayed()
    }

    @Test
    fun withWeatherOnEveryControlStillHasALabel() {
        show(world.weather())
        assertAllLabelled()

        compose.onNodeWithContentDescription("$tokyoCard, 21° and partly cloudy").performClick()
        assertAllLabelled()
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
