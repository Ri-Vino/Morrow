package app.morrow.ui.world

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CityInkTest {

    // Where the card's words sit across its width: the name and relative day on the left, the clock on the right.
    private val leftText = listOf(0f, 0.15f, 0.3f, 0.45f)
    private val rightText = listOf(0.65f, 0.8f, 1f)

    private fun everyTenMinutes() = (0 until 24 * 60 step 10).map { it to SkyCurve.at(it.toFloat()) }

    private fun washed(sky: Color, ink: CardInk, fraction: Float) = ink.scrim.copy(alpha = ink.scrimAlphaAt(fraction)).compositeOver(sky)

    @Test
    fun daylightGetsDarkInkAndNightGetsLight() {
        assertEquals(Illustration.cityInkDark, inkFor(SkyCurve.at(12 * 60f)).text)
        assertEquals(Illustration.cityInkLight, inkFor(SkyCurve.at(0f)).text)
    }

    @Test
    fun theWashIsPaleUnderDarkInkAndDarkUnderLightInk() {
        assertEquals(Illustration.cityScrimLight, inkFor(SkyCurve.at(12 * 60f)).scrim)
        assertEquals(Illustration.cityScrimDark, inkFor(SkyCurve.at(0f)).scrim)
    }

    @Test
    fun theWashFadesToNothingBeforeTheMiddleAndBuildsAgain() {
        val ink = inkFor(SkyCurve.at(0f))
        assertEquals(ink.scrimLeft, ink.scrimAlphaAt(0f))
        assertEquals(0f, ink.scrimAlphaAt(SCRIM_CLEAR_AT))
        assertEquals(ink.scrimRight, ink.scrimAlphaAt(1f))
    }

    @Test
    fun inkReadsOnTheBareSkyAllDay() {
        for ((minute, sky) in everyTenMinutes()) {
            val ratio = contrastRatio(sky, inkFor(sky).text)
            assertTrue(ratio >= 3f, "Ink on the bare sky at minute $minute was only $ratio:1")
        }
    }

    @Test
    fun inkReadsOnTheSkyUnderTheWashAllDay() {
        for ((minute, sky) in everyTenMinutes()) {
            val ink = inkFor(sky)
            for (fraction in leftText + rightText) {
                val ratio = contrastRatio(washed(sky, ink, fraction), ink.text)
                assertTrue(ratio >= 4.5f, "Ink at minute $minute, ${(fraction * 100).toInt()}% across, was only $ratio:1")
            }
        }
    }

    @Test
    fun theWashNeverMakesTheBareSkyHarderToRead() {
        for ((minute, sky) in everyTenMinutes()) {
            val ink = inkFor(sky)
            for (fraction in leftText + rightText) {
                assertTrue(
                    contrastRatio(washed(sky, ink, fraction), ink.text) >= contrastRatio(sky, ink.text),
                    "The wash lowered contrast at minute $minute, ${(fraction * 100).toInt()}% across",
                )
            }
        }
    }
}
