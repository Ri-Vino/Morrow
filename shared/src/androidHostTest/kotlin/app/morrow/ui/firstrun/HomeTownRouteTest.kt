package app.morrow.ui.firstrun

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import app.morrow.ui.world.WorldFixtures
import app.morrow.weather.WeatherPlaceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class HomeTownRouteTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val world = WorldFixtures()
    private var finished = 0
    private var backs = 0
    private var weatherChanges = 0

    private fun show() {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                HomeTownRoute(
                    world.search, world.cities, world.settings,
                    onBack = { backs++ },
                    onFinished = { finished++ },
                    onWeatherChanged = { weatherChanges++ },
                )
            }
        }
    }

    private fun waitFor(description: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theStepAsksWhereHomeIsAndOffersToSkip() {
        show()

        compose.onNodeWithText("Where’s home?").assertIsDisplayed()
        compose.onNodeWithText("Pip shows the weather there on Home. You can change it any time in Settings.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search for a city").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        // The title is a heading.
        val heading = compose.onNodeWithText("Where’s home?").fetchSemanticsNode().config
        assertEquals(true, heading.getOrNull(SemanticsProperties.Heading) != null)
    }

    @Test
    fun pickingACityMakesItHomeAndHomesWeatherFollowsIt() {
        show()

        waitFor("Make Tokyo your home city")
        compose.onNodeWithContentDescription("Make Tokyo your home city").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { finished == 1 }
        assertEquals("Tokyo", runBlocking { world.cities.home() }?.city?.name)
        assertEquals(WeatherPlaceMode.HOME, runBlocking { world.settings.weatherSource.first() })
        assertEquals(1, weatherChanges)
    }

    @Test
    fun aCityFoundByTypingWorksOffline() {
        show()

        compose.onNodeWithContentDescription("Search for a city").performTextInput("Birm")
        // The list follows the typing a moment behind, so wait for Popular to go.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("POPULAR").fetchSemanticsNodes().isEmpty() }
        waitFor("Make Birmingham your home city")
        compose.onNodeWithContentDescription("Make Birmingham your home city").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { finished == 1 }
        assertEquals("Birmingham", runBlocking { world.cities.home() }?.city?.name)
    }

    @Test
    fun skippingFinishesWithoutAHomeCityOrAChangeOfWeather() {
        // The phone's own city is home when the app first starts; skipping un-homes it, and it stays on World.
        runBlocking { world.cities.ensureSeeded() }
        show()

        compose.onNodeWithText("Skip").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { finished == 1 }
        assertNull(runBlocking { world.cities.home() })
        assertEquals(listOf("London"), runBlocking { world.cities.cities.first() }.map { it.city.name })
        assertEquals(WeatherPlaceMode.AUTO, runBlocking { world.settings.weatherSource.first() })
        assertEquals(0, weatherChanges)
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show()
        waitFor("Make Tokyo your home city")

        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }
}
