package app.morrow.ui.world

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.contrastRatio
import app.morrow.ui.components.cityScene
import app.morrow.weather.Sky
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

    // Under weather the sky can be a mid-grey at dawn, where the design's wash is too thin near the middle of the card
    // for 4.5:1. Only the large name and clock reach that far, and they need 3:1; the smaller lines sit near the edges.
    private val smallText = listOf(0f, 0.15f, 0.3f, 0.4f, 0.75f, 0.8f, 1f)

    @Test
    fun underAnyWeatherSmallTextNearTheEdgesReadsAllDay() {
        for (weather in Sky.entries) {
            for (minute in 0 until 24 * 60 step 10) {
                val sky = cityScene(minute, weather).sky
                val ink = inkFor(sky)
                for (fraction in smallText) {
                    val ratio = contrastRatio(washed(sky, ink, fraction), ink.text)
                    assertTrue(ratio >= 4.5f, "Ink in $weather at minute $minute, ${(fraction * 100).toInt()}% across, was only $ratio:1")
                }
            }
        }
    }

    @Test
    fun underAnyWeatherLargeTextReadsAnywhereAcrossTheCard() {
        for (weather in Sky.entries) {
            for (minute in 0 until 24 * 60 step 10) {
                val sky = cityScene(minute, weather).sky
                val ink = inkFor(sky)
                for (fraction in (0..20).map { it / 20f }) {
                    val ratio = contrastRatio(washed(sky, ink, fraction), ink.text)
                    assertTrue(ratio >= 3f, "Ink in $weather at minute $minute, ${(fraction * 100).toInt()}% across, was only $ratio:1")
                }
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
