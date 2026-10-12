package app.morrow.weather

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A forecast older than this has its "now" replaced by the hour it has for the time it is. */
private val NowIsFreshFor = 1.hours

private val DaylightHours = 6..18

/**
 * The weather at [now], or null when the forecast no longer knows it. A forecast fetched within the hour is trusted
 * for its current reading; an older one has its hour for the time it is, so a night's old temperature isn't shown as
 * the morning's, and once its hours run out there is nothing to show.
 */
fun Forecast.conditionsAt(now: Instant): CurrentWeather? {
    if (now - fetchedAt <= NowIsFreshFor) return this.now
    val hour = hours.firstOrNull { it.time <= now && now < it.time + 1.hours } ?: return null
    return CurrentWeather(hour.temperature, hour.sky, isDay = isDaylight(hour.time))
}

private fun Forecast.isDaylight(at: Instant): Boolean {
    val place = try {
        TimeZone.of(timeZone)
    } catch (_: IllegalArgumentException) {
        return now.isDay
    }
    return at.toLocalDateTime(place).hour in DaylightHours
}

/** The forecast, if it may be shown at [now]: there is one, and it's no more than a day old. */
fun PlaceWeather.forecastAt(now: Instant): Forecast? = forecast?.takeIf { ageAt(now) != WeatherAge.Expired }
