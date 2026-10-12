package app.morrow.ui

import app.morrow.weather.Sky
import app.morrow.weather.Units
import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherTextTest {

    @Test
    fun temperaturesAreWholeDegrees() {
        assertEquals("12°", degrees(12.4, Units.CELSIUS))
        assertEquals("13°", degrees(12.5, Units.CELSIUS))
        assertEquals("−3°", degrees(-3.4, Units.CELSIUS))
    }

    @Test
    fun aTemperatureJustBelowZeroIsNotMinusZero() {
        assertEquals("0°", degrees(-0.4, Units.CELSIUS))
    }

    @Test
    fun fahrenheitIsConvertedBeforeRounding() {
        assertEquals("50°", degrees(10.0, Units.FAHRENHEIT))
        assertEquals("54°", degrees(12.4, Units.FAHRENHEIT))
        assertEquals("27°", degrees(-3.0, Units.FAHRENHEIT))
    }

    @Test
    fun eachSkyHasTheCanvassName() {
        assertEquals(
            listOf("Clear", "Partly cloudy", "Cloudy", "Fog", "Drizzle", "Rain", "Snow", "Thunderstorms"),
            Sky.entries.map { it.displayName },
        )
    }
}
