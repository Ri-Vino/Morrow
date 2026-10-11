package app.morrow.ui.world

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.morrow.places.City
import app.morrow.platform.HttpException
import app.morrow.platform.HttpResponse
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import app.morrow.weather.HebdenSearchJson
import app.morrow.weather.LonSearchJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AddCityScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val world = WorldFixtures()
    private var backs = 0

    private fun show(purpose: CityPickPurpose = CityPickPurpose.ADD) {
        runBlocking { world.cities.ensureSeeded() }
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                AddCityRoute(world.search, world.cities, onBack = { backs++ }, purpose = purpose, ticks = world.ticks, phoneZone = world.phoneZone)
            }
        }
    }

    private fun savedNames() = runBlocking { world.cities.cities.first() }.map { it.city.name }

    private fun type(text: String) = compose.onNodeWithContentDescription("Search for a city").performTextInput(text)

    private fun waitFor(description: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }

    private fun waitUntilGone(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theSearchFieldIsFocusedOnOpen() {
        show()

        compose.onNodeWithContentDescription("Search for a city").assertIsFocused()
        compose.onNodeWithText("Add a city").assertIsDisplayed()
    }

    @Test
    fun withNothingTypedItSuggestsPopularCitiesNotYetSaved() {
        show()

        waitFor("Add Tokyo")
        compose.onNodeWithText("POPULAR").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add New York City").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Birmingham").assertIsDisplayed()
        // London is home already.
        compose.onNodeWithContentDescription("Add London").assertDoesNotExist()
        compose.onNodeWithContentDescription("London is already in your cities").assertDoesNotExist()
    }

    @Test
    fun typingFindsACityWithItsPlaceAndLocalTime() {
        show()

        type("tok")

        waitUntilGone("POPULAR")
        waitFor("Add Tokyo")
        compose.onNodeWithText("Tokyo").assertIsDisplayed()
        compose.onNodeWithText("Tokyo, Japan").assertIsDisplayed()
        compose.onNodeWithText("16:40").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add New York City").assertDoesNotExist()
    }

    @Test
    fun accentsDontGetInTheWay() {
        show()

        type("reykjavik")

        waitUntilGone("POPULAR")
        waitFor("Add Reykjavík")
        compose.onNodeWithText("Capital Region, Iceland").assertIsDisplayed()
    }

    @Test
    fun plusAddsTheCityAndGoesBackToWorld() {
        show()
        type("tok")
        waitUntilGone("POPULAR")

        compose.onNodeWithContentDescription("Add Tokyo").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { backs == 1 }
        assertEquals(listOf("London", "Tokyo"), savedNames())
    }

    @Test
    fun aSavedCityShowsATickAndCantBeAddedAgain() {
        show()
        type("lon")

        waitUntilGone("POPULAR")
        waitFor("London is already in your cities")

        compose.onNodeWithContentDescription("Add London").assertDoesNotExist()
        compose.onNodeWithText("England, United Kingdom").assertIsDisplayed()
    }

    @Test
    fun nothingFoundSaysSo() {
        show()

        type("zzz")

        waitForText("No places by that name.")
        compose.onNodeWithContentDescription("Add Tokyo").assertDoesNotExist()
    }

    @Test
    fun clearingTheTextBringsBackThePopularCities() {
        show()
        type("zzz")
        waitForText("No places by that name.")

        compose.onNodeWithContentDescription("Search for a city").performTextReplacement("")

        waitFor("Add Tokyo")
        compose.onNodeWithText("POPULAR").assertIsDisplayed()
        compose.onNodeWithText("No places by that name.").assertDoesNotExist()
    }

    @Test
    fun theBackButtonLeavesWithoutAdding() {
        show()
        waitFor("Add Tokyo")

        compose.onNodeWithContentDescription("Back to World").performClick()

        assertEquals(1, backs)
        assertEquals(listOf("London"), savedNames())
    }

    @Test
    fun lookingForMorePlacesShowsUntilOnlineResultsJoinTheList() {
        world.geocoderAnswer = { HttpResponse(200, HebdenSearchJson) }
        show()
        waitFor("Add Tokyo")
        compose.mainClock.autoAdvance = false

        type("hebden")
        compose.mainClock.advanceTimeBy(250)

        compose.onNodeWithText("Looking for more places…").assertIsDisplayed()
        assertEquals(emptyList(), world.http.urls, "online search waits for the typing to stop")

        compose.mainClock.advanceTimeBy(500)
        waitFor("Add Hebden Bridge")
        compose.onNodeWithText("Looking for more places…").assertDoesNotExist()
        compose.onNodeWithText("No places by that name.").assertDoesNotExist()
        assertEquals(1, world.http.urls.size)
        assertTrue(world.http.urls.single().contains("name=hebden"))
        assertTrue("hebden" in world.searchCache.rows)
    }

    @Test
    fun onlinePlacesAndBundledOnesAreOneListAndAnOnlineCitySavesWhole() {
        world.geocoderAnswer = { HttpResponse(200, LonSearchJson) }
        show()

        type("lon")
        waitFor("Add Little London")

        // London from both lists shows once, ticked; the other London and Little London follow, smaller.
        assertEquals(1, compose.onAllNodesWithContentDescription("London is already in your cities").fetchSemanticsNodes().size)
        compose.onNodeWithText("Ontario, Canada").assertIsDisplayed()
        val names = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .filter { it.startsWith("Add ") }
        assertEquals(listOf("Add London", "Add Little London"), names)

        compose.onNodeWithContentDescription("Add London").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { backs == 1 }
        val ontario = runBlocking { world.cities.cities.first() }.last().city
        assertEquals(City(6058560, "London", "Ontario", "Canada", 42.98339, -81.23304, "America/Toronto", 346765), ontario)
    }

    @Test
    fun aFailedOnlineSearchKeepsBundledResultsWithAnOfflineNote() {
        world.geocoderAnswer = { throw HttpException("offline") }
        show()

        type("tok")

        waitForText("You’re offline. Places on this phone still work; connect to find smaller towns.")
        compose.onNodeWithContentDescription("Add Tokyo").assertIsDisplayed()
        compose.onNodeWithText("Looking for more places…").assertDoesNotExist()
    }

    @Test
    fun withWeatherOffOnlyTheBundledListIsSearched() {
        runBlocking { world.settings.setWeatherEnabled(false) }
        world.geocoderAnswer = { HttpResponse(200, HebdenSearchJson) }
        show()

        type("hebden")
        waitForText("No places by that name.")
        compose.mainClock.advanceTimeBy(2_000)

        assertEquals(emptyList(), world.http.urls)
        compose.onNodeWithContentDescription("Add Hebden Bridge").assertDoesNotExist()
        compose.onNodeWithText("Looking for more places…").assertDoesNotExist()
        compose.onNodeWithText("You’re offline. Places on this phone still work; connect to find smaller towns.").assertDoesNotExist()
    }

    @Test
    fun pickingAHomeCitySavesItAndMakesItHome() {
        show(CityPickPurpose.HOME)

        waitFor("Make Tokyo your home city")
        compose.onNodeWithText("Home city").assertIsDisplayed()
        compose.onNodeWithContentDescription("London is your home city").assertIsDisplayed()
        compose.onNodeWithContentDescription("Make Tokyo your home city").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { backs == 1 }
        val saved = runBlocking { world.cities.cities.first() }
        assertEquals(listOf("London", "Tokyo"), saved.map { it.city.name })
        assertEquals("Tokyo", saved.single { it.isHome }.city.name)
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show()
        waitFor("Add Tokyo")

        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }
}
