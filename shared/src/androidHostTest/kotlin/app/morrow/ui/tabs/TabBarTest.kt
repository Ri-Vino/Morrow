package app.morrow.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TabBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = NoMotion)

    private var tab by mutableStateOf(Tab.ALARMS)

    private fun show() {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                TabHost(tab, onSelect = { tab = it }, world = { BasicText("World screen") }) { BasicText("Alarms screen") }
            }
        }
    }

    @Test
    fun tappingATabSelectsItAndShowsItsScreen() {
        show()
        compose.onNodeWithText("Alarms screen").assertExists()
        tabNode("Alarms").assertIsSelected()
        tabNode("World").assertIsNotSelected()

        compose.onNodeWithText("World").performClick()
        compose.onNodeWithText("World screen").assertExists()
        compose.onNodeWithText("Alarms screen").assertDoesNotExist()
        tabNode("World").assertIsSelected()

        compose.onNodeWithText("Timers").performClick()
        compose.onNodeWithText("Your timers will live here.").assertExists()
        tabNode("Timers").assertIsSelected()
        tabNode("World").assertIsNotSelected()

        compose.onNodeWithText("Alarms").performClick()
        compose.onNodeWithText("Alarms screen").assertExists()
        assertEquals(Tab.ALARMS, tab)
    }

    @Test
    fun backFromWorldOrTimersReturnsToAlarms() {
        show()

        compose.onNodeWithText("Timers").performClick()
        pressBack()
        assertEquals(Tab.ALARMS, tab)
        compose.onNodeWithText("Alarms screen").assertExists()

        compose.onNodeWithText("World").performClick()
        pressBack()
        assertEquals(Tab.ALARMS, tab)
    }

    @Test
    fun everyTabIsSelectableAndLabelled() {
        show()

        assertEquals(3, compose.onAllNodes(isSelectable()).fetchSemanticsNodes().size)
        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }

    @Test
    fun theBarStepsAsideForASheet() {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                TabHost(Tab.WORLD, onSelect = {}, barHidden = true, world = { BasicText("World screen") }) { BasicText("Alarms screen") }
            }
        }

        compose.onNodeWithText("World screen").assertExists()
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
    }

    private fun tabNode(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun pressBack() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
