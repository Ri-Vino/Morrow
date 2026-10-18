package app.morrow.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkyCurveTest {

    @Test
    fun keyframesAreExact() {
        assertEquals(Color(0xFF0F1630), SkyCurve.at(0f))
        assertEquals(Color(0xFFE0907A), SkyCurve.at(6 * 60 + 30f))
        assertEquals(Color(0xFFF9DFA6), SkyCurve.at(12 * 60f))
        assertEquals(Color(0xFF7A3A50), SkyCurve.at(19 * 60 + 20f))
    }

    @Test
    fun interpolatesBetweenKeyframesInSrgb() {
        // Halfway between 05:00 #1C1F3C and 05:50 #5C2F48.
        assertEquals(Color(0xFF3C2742), SkyCurve.at(5 * 60 + 25f))
    }

    @Test
    fun wrapsAroundMidnight() {
        assertEquals(SkyCurve.at(0f), SkyCurve.at(24 * 60f))
        assertEquals(SkyCurve.at(60f), SkyCurve.at(25 * 60f))
    }

    @Test
    fun sceneInkFlipsWithTheSky() {
        assertTrue(SkyCurve.at(12 * 60f).isLightSky())
        assertFalse(SkyCurve.at(0f).isLightSky())
        assertFalse(SkyCurve.at(21 * 60f).isLightSky())
    }

    @Test
    fun sunCrossesTheHorizonAtSixAndEighteen() {
        assertClose(-1.0, sunElevation(0f))
        assertClose(0.0, sunElevation(6 * 60f))
        assertClose(1.0, sunElevation(12 * 60f))
        assertClose(0.0, sunElevation(18 * 60f))
    }

    @Test
    fun mixRoundsToWholeChannels() {
        assertEquals(Color(0xFF808080), mix(Color(0xFF000000), Color(0xFFFFFFFF), 0.5f))
        assertEquals(Color(0xFF000000), mix(Color(0xFF000000), Color(0xFFFFFFFF), 0f))
    }

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected but was $actual")
}
