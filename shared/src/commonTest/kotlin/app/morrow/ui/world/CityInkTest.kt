package app.morrow.ui.world

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

    @Test
    fun daylightGetsDarkInkAndNightGetsLight() {
        assertEquals(Illustration.cityInkDark, inkFor(SkyCurve.at(12 * 60f)).text)
        assertEquals(Illustration.cityInkLight, inkFor(SkyCurve.at(0f)).text)
    }

    @Test
    fun theWashIsPaleUnderDarkInkAndDarkUnderLightInk() {
        assertEquals(Illustration.cityScrimLight, inkFor(SkyCurve.at(12 * 60f)).wash.copy(alpha = 1f))
        assertEquals(Illustration.cityScrimDark, inkFor(SkyCurve.at(0f)).wash.copy(alpha = 1f))
    }

    @Test
    fun underAnyWeatherEveryLineReadsAllDay() {
        for (weather in listOf(null) + Sky.entries) {
            for (minute in 0 until 24 * 60 step 10) {
                val sky = cityScene(minute, weather).sky
                val ink = inkFor(sky)
                val ratio = contrastRatio(ink.wash.compositeOver(sky), ink.text)
                assertTrue(ratio >= 4.5f, "Ink in ${weather ?: "clear"} at minute $minute was only $ratio:1")
            }
        }
    }

    @Test
    fun theWashNeverMakesTheBareSkyHarderToRead() {
        for (minute in 0 until 24 * 60 step 10) {
            val sky = SkyCurve.at(minute.toFloat())
            val ink = inkFor(sky)
            assertTrue(contrastRatio(ink.wash.compositeOver(sky), ink.text) >= contrastRatio(sky, ink.text))
        }
    }
}
