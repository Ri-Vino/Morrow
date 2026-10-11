package app.morrow.weather

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

@Serializable
data class Coordinates(val latitude: Double, val longitude: Double) {
    /** To 2 decimal places, about 1 km: the only form that is ever stored or sent. */
    fun rounded(): Coordinates = Coordinates(latitude.toHundredths() / 100.0, longitude.toHundredths() / 100.0)

    /** "51.51,-0.13": the rounded place as text, for URLs and as a cache key. */
    val key: String get() = "${latitude.twoDecimals()},${longitude.twoDecimals()}"
}

private fun Double.toHundredths(): Long = (this * 100).roundToLong()

/** Exactly two decimals, written by hand so no platform formatting can add more. */
internal fun Double.twoDecimals(): String {
    val hundredths = toHundredths()
    val sign = if (hundredths < 0) "-" else ""
    val whole = abs(hundredths) / 100
    val cents = (abs(hundredths) % 100).toString().padStart(2, '0')
    return "$sign$whole.$cents"
}
