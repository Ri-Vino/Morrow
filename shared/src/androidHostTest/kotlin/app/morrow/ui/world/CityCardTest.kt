package app.morrow.ui.world

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onRoot
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CityCardTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private var clicks = 0

    private fun show(isHome: Boolean = true, name: String = "London", fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MorrowTheme(ThemeKind.MORNING) {
                    CityCard(
                        name = name,
                        isHome = isHome,
                        relative = "Today · your time",
                        clock = "07:40",
                        minuteOfDay = 7 * 60 + 40,
                        onClick = { clicks++ },
                    )
                }
            }
        }
    }

    private fun bounds(text: String): DpRect =
        compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }

    private fun overlap(a: DpRect, b: DpRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test
    fun showsTheNameChipRelativeDayAndClock() {
        show()

        compose.onNodeWithText("London", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Today · your time", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("07:40", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun onlyTheHomeCityHasTheChip() {
        show(isHome = false, name = "New York")

        compose.onNodeWithText("New York", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun isOneNodeForScreenReaders() {
        show()

        compose.onNodeWithContentDescription("London, home city, 07:40, Today · your time").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        val node = compose.onNodeWithContentDescription("London, home city, 07:40, Today · your time").fetchSemanticsNode()
        assertEquals("Open London", node.config.getOrNull(SemanticsActions.OnClick)?.label)
    }

    @Test
    fun aCityThatIsNotHomeIsNotCalledHome() {
        show(isHome = false, name = "Tokyo")

        compose.onNodeWithContentDescription("Tokyo, 07:40, Today · your time").assertExists()
    }

    @Test
    fun clickingOpensTheCity() {
        show()

        compose.onNodeWithContentDescription("London, home city, 07:40, Today · your time").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun atDoubleTextSizeEverythingIsStillShownAndNothingOverlaps() {
        show(fontScale = 2f)

        val name = bounds("London")
        val chip = bounds("Home")
        val relative = bounds("Today · your time")
        val clock = bounds("07:40")
        listOf("London", "Home", "Today · your time", "07:40").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        val rows = listOf(name, chip, relative, clock)
        for (i in rows.indices) for (j in i + 1 until rows.size) {
            assertTrue(!overlap(rows[i], rows[j]), "Text $i overlaps text $j: ${rows[i]} and ${rows[j]}")
        }
        val card = compose.onRoot().getBoundsInRoot()
        assertTrue(card.bottom - card.top > 128.dp, "The card should grow with the text, was ${card.bottom - card.top}")
    }

    @Test
    fun aLongNameGivesWayToTheClockAtNormalSize() {
        show(name = "Sao Tome and Principe, a very long city name")

        compose.onNodeWithText("07:40", useUnmergedTree = true).assertIsDisplayed()
        val name = bounds("Sao Tome and Principe, a very long city name")
        assertTrue(!overlap(name, bounds("07:40")), "The name runs under the clock")
    }
}
