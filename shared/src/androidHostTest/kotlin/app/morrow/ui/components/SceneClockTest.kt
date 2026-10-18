package app.morrow.ui.components

import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The looping scenes run with motion on, and hold still when the phone asks for less. */
@RunWith(RobolectricTestRunner::class)
class SceneClockTest {

    @get:Rule
    val compose = createComposeRule()

    private fun secondsAfterASecond(reduceMotion: Boolean): Float {
        lateinit var seconds: State<Float>
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING, reduceMotion = reduceMotion) {
                seconds = rememberSceneSeconds(still = 7f, fullRateFor = 10f)
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        return seconds.value
    }

    @Test
    fun withMotionTheSceneRuns() {
        val seconds = secondsAfterASecond(reduceMotion = false)

        assertTrue(seconds in 0.5f..1.5f, "The scene should have run about a second, not $seconds")
    }

    @Test
    fun withLessMotionTheSceneHoldsStill() {
        assertEquals(7f, secondsAfterASecond(reduceMotion = true))
    }
}
