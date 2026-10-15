package app.morrow.ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.FakeSpeaker
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import app.morrow.weather.Coordinates
import app.morrow.weather.HerePlace
import app.morrow.weather.HereSource
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class WeatherSettingsScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val settings = MorrowSettings(InMemoryPreferences())
    private val catalog = CityCatalog { "2643743\tLondon\t\tEngland\tUnited Kingdom\t51.5085\t-0.1257\tEurope/London\t8961989" }
    private val cities = CityRepository(FakeSavedCityDao(), catalog) { "Europe/London" }
    private val here = MutableStateFlow<HerePlace?>(HerePlace(Coordinates(53.8, -1.55), "Leeds", HereSource.LOCATION))
    private val permissions = object : PermissionGate {
        override val asked = AndroidPermissions
        override val missing: StateFlow<List<AlarmPermission>> = MutableStateFlow(listOf(AlarmPermission.LOCATION))
        val requested = mutableListOf<AlarmPermission>()
        override fun refresh() = Unit
        override fun request(permission: AlarmPermission) {
            requested += permission
        }
    }
    private var homePicks = 0
    private var weatherChanges = 0

    private fun show(country: String = "GB") {
        runBlocking { cities.ensureSeeded() }
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                SettingsRoute(
                    settings = settings,
                    permissions = permissions,
                    cities = cities,
                    here = here,
                    phoneCountry = country,
                    speaker = FakeSpeaker(),
                    developerTools = null,
                    onPickHomeCity = { homePicks++ },
                    onWeatherChanged = { weatherChanges++ },
                    onBack = {},
                )
            }
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theWeatherSectionShowsWhereItIsAndTheHomeCity() {
        show()

        waitForText("Show the weather")
        compose.onNodeWithText("On Home, World and the Morning screen.").assertIsDisplayed()
        compose.onNodeWithText("Your rough location while Morrow is open, or the last place it knew. Now: Leeds.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("London").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Where I am").assertIsSelected()
        compose.onNodeWithContentDescription("Celsius").assertIsSelected()
    }

    @Test
    fun turningWeatherOffSavesItAndHidesItsChoices() {
        show()
        waitForText("Show the weather")

        compose.onNodeWithText("Show the weather").assertIsOn().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { !settings.weatherEnabled.first() } }
        compose.onNodeWithText("Show the weather").assertIsOff()
        compose.onNodeWithText("Where I am").assertDoesNotExist()
        compose.onNodeWithText("Units").assertDoesNotExist()
        assertEquals(0, weatherChanges)

        compose.onNodeWithText("Show the weather").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { settings.weatherEnabled.first() } }
        compose.waitUntil(timeoutMillis = 5_000) { weatherChanges == 1 }
    }

    @Test
    fun homeCityModeSavesItAndSaysSo() {
        show()
        waitForText("Show the weather")

        compose.onNode(hasText("Home city") and isSelectable()).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { settings.weatherSource.first() } == WeatherPlaceMode.HOME }
        waitForText("Always London, wherever you are.")
        compose.waitUntil(timeoutMillis = 5_000) { weatherChanges == 1 }
    }

    @Test
    fun unitsFollowTheCountryUntilPicked() {
        show(country = "US")
        waitForText("Show the weather")
        compose.onNodeWithContentDescription("Fahrenheit").performScrollTo().assertIsSelected()
        assertNull(runBlocking { settings.units.first() })

        compose.onNodeWithContentDescription("Celsius").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { settings.units.first() } == Units.CELSIUS }
        compose.onNodeWithContentDescription("Celsius").assertIsSelected()
    }

    @Test
    fun theHomeCityRowOpensThePicker() {
        show()
        waitForText("Show the weather")

        compose.onNodeWithText("London").performScrollTo().performClick()

        assertEquals(1, homePicks)
    }

    @Test
    fun locationIsAnOptionalPermissionHere() {
        show()
        waitForText("Show the weather")

        compose.onNodeWithContentDescription("Allow location").performScrollTo().performClick()

        assertEquals(listOf(AlarmPermission.LOCATION), permissions.requested)
    }

    @Test
    fun openMeteoIsCredited() {
        show()
        waitForText("Show the weather")

        compose.onNodeWithText("Weather data by Open-Meteo.com (CC BY 4.0)").performScrollTo().assertIsDisplayed()
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show()
        waitForText("Show the weather")

        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }
}
