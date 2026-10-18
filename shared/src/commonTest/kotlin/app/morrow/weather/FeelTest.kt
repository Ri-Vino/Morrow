package app.morrow.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeelTest {
    @Test
    fun bandsFollowTheRoundedTemperature() {
        val cases = listOf(
            -12.0 to Warmth.FREEZING,
            0.4 to Warmth.FREEZING,
            0.5 to Warmth.COLD,
            7.4 to Warmth.COLD,
            7.5 to Warmth.MILD,
            18.0 to Warmth.MILD,
            26.4 to Warmth.MILD,
            26.5 to Warmth.HOT,
            32.4 to Warmth.HOT,
            32.5 to Warmth.SCORCHING,
            41.0 to Warmth.SCORCHING,
        )
        for ((celsius, warmth) in cases) assertEquals(warmth, feelOf(celsius, wind = null).warmth, "at $celsius°")
    }

    @Test
    fun windyFromThirtyKilometresAnHour() {
        assertFalse(feelOf(12.0, wind = 29.9).windy)
        assertTrue(feelOf(12.0, wind = 30.0).windy)
        assertFalse(feelOf(12.0, wind = null).windy)
    }
}
