package app.morrow.weather

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What the weather is doing at one moment: the temperature in °C and the sky. */
data class Conditions(val temperature: Double, val sky: Sky)

/**
 * The weather at [now]: as fetched while still in the hour it was fetched in, then the forecast for the hour [now]
 * falls in, so an older forecast isn't shown as if it were current. Past the forecast's last hour, as fetched.
 */
fun Forecast.conditionsAt(now: Instant): Conditions {
    val hour = hours.lastOrNull { it.time <= now }?.takeIf { now < it.time + 1.hours && it.time > fetchedAt }
    return if (hour != null) Conditions(hour.temperature, hour.sky) else Conditions(this.now.temperature, this.now.sky)
}

/** The forecast, if it may be shown at [now]: there is one, and it's no more than a day old. */
fun PlaceWeather.forecastAt(now: Instant): Forecast? = forecast?.takeIf { ageAt(now) != WeatherAge.Expired }
