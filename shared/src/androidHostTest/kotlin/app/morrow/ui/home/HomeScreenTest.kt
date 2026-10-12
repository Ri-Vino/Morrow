package app.morrow.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

    private val rain = HomeWeather(Sky.RAIN, night = false, text = "12° · Rain in London", label = "12° and rain in London. Open World")

    private fun show(weather: HomeWeather?) {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                HomeScreen(
                    model = homeModel(emptyList(), now, london, emptyList(), weather = weather),
                    theme = ThemeKind.MORNING,
                    minuteOfDay = 7 * 60 + 40f,
                    actions = HomeActions(
                        onToggle = { _, _ -> },
                        onOpenAlarm = {},
                        onNewAlarm = {},
                        onTrySunrise = {},
                        onSettings = {},
                        onFixPermissions = {},
                        onOpenWorld = { worldOpened++ },
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
}
