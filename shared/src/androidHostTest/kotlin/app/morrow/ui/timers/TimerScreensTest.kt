package app.morrow.ui.timers

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.morrow.ui.NoMotion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TimerDoneScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private var stops = 0
    private var minutes = 0

    private val model = TimerDoneModel(
        timers = listOf(
            DoneTimer(1, "Take the pasta off the hob", "10 min timer · ended at 12:42", hasNote = true),
            DoneTimer(2, "Your 25 min timer is done", "25 min timer · ended at 12:42", hasNote = false),
        ),
        stopLabel = "Stop all 2 timers",
        addMinuteLabel = "Ring all 2 again in a minute",
    )

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TimerDoneScreen(model, onStop = { stops++ }, onAddMinute = { minutes++ })
            }
        }
    }

    @Test
    fun showsEveryTimerThatEndedAndItsButtonsActOnAllOfThem() {
        show()

        compose.onNodeWithText("TIMER DONE").assertIsDisplayed()
        compose.onNodeWithText("Take the pasta off the hob").assertIsDisplayed()
        compose.onNodeWithText("Your 25 min timer is done").assertIsDisplayed()
        compose.onNodeWithText("Stops by itself after 10 minutes.").assertIsDisplayed()

        compose.onNodeWithContentDescription("Stop all 2 timers").performClick()
        compose.onNodeWithContentDescription("Ring all 2 again in a minute").performClick()
        assertEquals(1, stops)
        assertEquals(1, minutes)
    }

    @Test
    fun atDoubleTextSizeTheNotesScrollAndTheButtonsStayOnScreen() {
        show(fontScale = 2f)

        compose.onNodeWithContentDescription("Stop all 2 timers").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ring all 2 again in a minute").assertIsDisplayed()
        compose.onNodeWithText("25 min timer · ended at 12:42").performScrollTo().assertIsDisplayed()
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show()
        assertEquals(emptyList(), unlabelled())
    }

    private fun unlabelled() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
        val config = node.config
        config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
    }.map { it.config.toString() }
}
