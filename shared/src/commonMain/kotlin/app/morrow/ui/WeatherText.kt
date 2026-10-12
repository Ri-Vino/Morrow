package app.morrow.ui

import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.toFahrenheit
import kotlin.math.roundToInt

/** A °C temperature as a whole number of degrees in [units], like "12°" or "−3°". */
fun degrees(celsius: Double, units: Units): String {
    val value = (if (units == Units.FAHRENHEIT) celsius.toFahrenheit() else celsius).roundToInt()
    return if (value < 0) "−${-value}°" else "$value°"
}

/** The sky's name, as Home and the World cards say it: "Rain", "Thunderstorms". */
val Sky.displayName: String
    get() = when (this) {
        Sky.CLEAR -> "Clear"
        Sky.PARTLY_CLOUDY -> "Partly cloudy"
        Sky.CLOUDY -> "Cloudy"
        Sky.FOG -> "Fog"
        Sky.DRIZZLE -> "Drizzle"
        Sky.RAIN -> "Rain"
        Sky.SNOW -> "Snow"
        Sky.STORM -> "Thunderstorms"
    }
