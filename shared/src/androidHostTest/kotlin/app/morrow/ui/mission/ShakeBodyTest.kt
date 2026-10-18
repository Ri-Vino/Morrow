package app.morrow.ui.mission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.morrow.missions.ShakeState
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/** Someone who can't shake the phone this morning isn't stuck: sums are offered after a minute. */
@RunWith(RobolectricTestRunner::class)
class ShakeBodyTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private var switched = 0

    private fun show(sensorAvailable: Boolean) {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                ShakeBody(ShakeState(shakes = 3, goal = 30), sensorAvailable, onSwitchToSums = { switched++ })
            }
        }
    }

    @Test
    fun sumsAreOfferedAfterAMinute() {
        compose.mainClock.autoAdvance = false
        show(sensorAvailable = true)

        compose.mainClock.advanceTimeBy(OFFER_SUMS_AFTER_MILLIS - 1_000)
        compose.onNodeWithText("Switch to sums instead").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Can’t shake right now? Sums wake you up just as well.").assertIsDisplayed()
        compose.onNodeWithText("Switch to sums instead").performClick()
        assertEquals(1, switched)
    }

    @Test
    fun withoutASensorSumsAreOfferedAtOnce() {
        compose.mainClock.autoAdvance = false
        show(sensorAvailable = false)
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Switch to sums instead").assertIsDisplayed()
    }
}
