package app.morrow.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import app.morrow.ui.NoMotion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/** Body text follows the phone's font size all the way; the display sizes stop at 1.3 times. */
@RunWith(RobolectricTestRunner::class)
class MorrowTypeTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    /** Each style's size on screen, in sp before the font scale is applied to it: [fontScale] × its fontSize. */
    private fun shownSizes(fontScale: Float): Map<String, Float> {
        var sizes = emptyMap<String, Float>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MorrowTheme(ThemeKind.MORNING) {
                    val type = MorrowTheme.type
                    sizes = mapOf(
                        "body" to type.body.fontSize.value * fontScale,
                        "title" to type.title.fontSize.value * fontScale,
                        "display" to type.display.fontSize.value * fontScale,
                        "hero" to type.hero.fontSize.value * fontScale,
                    )
                }
            }
        }
        compose.waitForIdle()
        return sizes
    }

    @Test
    fun atTheDefaultSizeNothingChanges() {
        val sizes = shownSizes(1f)

        assertEquals(16f, sizes.getValue("body"))
        assertEquals(40f, sizes.getValue("display"))
        assertEquals(104f, sizes.getValue("hero"))
    }

    @Test
    fun atTwiceTheSizeBodyAndTitlesDoubleButDisplaySizesStop() {
        val sizes = shownSizes(2f)

        assertEquals(32f, sizes.getValue("body"))
        assertEquals(48f, sizes.getValue("title"))
        assertEquals(40f * DISPLAY_SCALE_LIMIT, sizes.getValue("display"), 0.01f)
        assertEquals(104f * DISPLAY_SCALE_LIMIT, sizes.getValue("hero"), 0.01f)
    }
}
