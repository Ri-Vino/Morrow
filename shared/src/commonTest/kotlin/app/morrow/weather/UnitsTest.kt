package app.morrow.weather

import kotlin.test.Test
import kotlin.test.assertEquals

class UnitsTest {

    @Test
    fun fahrenheitOnlyInTheUsLiberiaAndMyanmar() {
        for (country in listOf("US", "LR", "MM", "us", "Mm")) assertEquals(Units.FAHRENHEIT, defaultUnits(country), country)
        for (country in listOf("GB", "CA", "DE", "JP", "AU", "IN", "", "XX")) assertEquals(Units.CELSIUS, defaultUnits(country), country)
    }

    @Test
    fun convertsCelsiusToFahrenheit() {
        assertEquals(32.0, 0.0.toFahrenheit())
        assertEquals(212.0, 100.0.toFahrenheit())
        assertEquals(-40.0, (-40.0).toFahrenheit())
        assertEquals(53.6, 12.0.toFahrenheit(), absoluteTolerance = 1e-9)
    }
}
