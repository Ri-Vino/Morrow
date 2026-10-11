package app.morrow.weather

enum class Units { CELSIUS, FAHRENHEIT }

private val FahrenheitCountries = setOf("US", "LR", "MM")

/** °F only in the US, Liberia and Myanmar, by ISO country code; °C everywhere else. */
fun defaultUnits(countryCode: String): Units =
    if (countryCode.uppercase() in FahrenheitCountries) Units.FAHRENHEIT else Units.CELSIUS

/** A °C temperature in °F. */
fun Double.toFahrenheit(): Double = this * 9 / 5 + 32
