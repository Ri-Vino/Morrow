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

@RunWith(RobolectricTestRunner::class)
class AddCityScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val world = WorldFixtures()
    private var backs = 0

    private fun show() {
        runBlocking { world.cities.ensureSeeded() }
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                AddCityRoute(world.catalog, world.cities, onBack = { backs++ }, ticks = world.ticks, phoneZone = world.phoneZone)
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
