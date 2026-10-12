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
import app.morrow.timers.Timer
import app.morrow.ui.NoMotion
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

private val london = TimeZone.of("Europe/London")
private val ended = LocalDateTime.parse("2026-10-12T12:42").toInstant(london)

/** The Timer done screen as the ring path builds it, with one timer or two, and with Pip reading or quiet. */
@RunWith(RobolectricTestRunner::class)
class TimerDoneLabelsTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val pasta = Timer(1, 10.minutes, ended, null, "Take the pasta off the hob", ended - 10.minutes)
    private val plain = Timer(2, 25.minutes, ended, null, "", ended - 25.minutes)

    private var stops = 0
    private var minutes = 0

    private fun show(timers: List<Timer>, speaking: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TimerDoneScreen(timerDoneModel(timers, london, speaking), onStop = { stops++ }, onAddMinute = { minutes++ })
            }
        }
    }

    @Test
    fun withOneTimerEveryControlHasALabelForThatTimer() {
        show(listOf(pasta))

        assertEquals(emptyList(), unlabelled())
        compose.onNodeWithText("Take the pasta off the hob").assertIsDisplayed()
        compose.onNodeWithText("10 min timer · ended at 12:42").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop the timer").performClick()
        compose.onNodeWithContentDescription("Ring again in a minute").performClick()
        assertEquals(1, stops)
        assertEquals(1, minutes)
    }

    @Test
    fun withTwoTimersEveryControlHasALabelForBoth() {
        show(listOf(pasta, plain), speaking = true)

        assertEquals(emptyList(), unlabelled())
        compose.onNodeWithContentDescription("Stop all 2 timers").performClick()
        compose.onNodeWithContentDescription("Ring all 2 again in a minute").performClick()
        assertEquals(1, stops)
        assertEquals(1, minutes)
    }

    @Test
    fun saysPipIsReadingWhileHeReadsTheNote() {
        show(listOf(pasta), speaking = true)

        compose.onNodeWithText("Pip is reading your note").assertIsDisplayed()
    }

    @Test
    fun saysNothingAboutReadingWithoutANote() {
        show(listOf(plain), speaking = true)

        compose.onNodeWithText("Your 25 min timer is done").assertIsDisplayed()
        compose.onNodeWithText("Pip is reading", substring = true).assertDoesNotExist()
    }

    @Test
    fun saysNothingAboutReadingWhilePipIsQuiet() {
        show(listOf(pasta), speaking = false)

        compose.onNodeWithText("Pip is reading", substring = true).assertDoesNotExist()
    }

    @Test
    fun atDoubleTextSizeTheButtonsStayOnScreenWhilePipReads() {
        show(listOf(pasta, plain), speaking = true, fontScale = 2f)

        compose.onNodeWithContentDescription("Stop all 2 timers").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ring all 2 again in a minute").assertIsDisplayed()
        compose.onNodeWithText("Pip is reading your note").performScrollTo().assertIsDisplayed()
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    private fun unlabelled() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
        val config = node.config
        config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
    }.map { it.config.toString() }
}
