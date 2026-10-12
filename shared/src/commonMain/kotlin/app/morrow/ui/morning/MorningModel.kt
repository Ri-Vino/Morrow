package app.morrow.ui.morning

import app.morrow.ui.formatDuration
import app.morrow.ui.hhmm
import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.WeatherAge
import app.morrow.weather.WeatherState
import app.morrow.weather.conditionsAt
import app.morrow.weather.dayAt
import app.morrow.weather.dayLine
import app.morrow.weather.toFahrenheit
import app.morrow.weather.weatherTip
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class MorningModel(val hello: String, val summary: String)

/**
 * The Morning screen's words for waking at [wokeAt]. [wentToBed] is a guess at bedtime: the summary
 * gives a length of sleep only when the guess makes a believable night.
 */
fun morningModel(wokeAt: Instant, wentToBed: Instant?, zone: TimeZone): MorningModel {
    val woke = wokeAt.toLocalDateTime(zone)
    val time = hhmm(woke.hour, woke.minute)
    val slept = wentToBed?.let { wokeAt - it }?.takeIf { it in BELIEVABLE_NIGHT }
    return MorningModel(
        hello = when {
            woke.hour < 12 -> "Good morning."
            woke.hour < 17 -> "Good afternoon."
            else -> "Good evening."
        },
        summary = if (slept != null) {
            "You were up at $time, after about ${formatDuration(slept)} of sleep."
        } else {
            "You were up at $time. Nicely done."
        },
    )
}

private val BELIEVABLE_NIGHT = 2.hours..14.hours

/** The weather card on the Morning screen. */
data class MorningWeather(
    val sky: Sky,
    /** After dark where the weather is, so a clear sky shows the moon. */
    val night: Boolean,
    /** "12° and rainy in London". */
    val headline: String,
    /** "High 14° · Low 8° · Rain until about 11:00". */
    val detail: String,
    /** Pip's tip for the day, or null when there is nothing worth saying. */
    val tip: String?,
    private val spoken: String,
) {
    /** What Pip reads out: the [hello], how it is outside, then the tip. */
    fun speech(hello: String): String = listOfNotNull(hello, spoken, tip).joinToString(" ")
}

/**
 * The card for the place the weather is for, or null to hide it: with weather off, with no forecast, when the
 * forecast isn't from today on the phone's clock or is over 24 hours old, or when it is older than an hour and its
 * hours have run out. Yesterday's weather, or a reading from hours ago, is never shown as how it is now.
 */
fun morningWeather(state: WeatherState, units: Units, now: Instant, zone: TimeZone): MorningWeather? {
    if (!state.enabled) return null
    val here = state.hereWeather ?: return null
    val forecast = here.forecast ?: return null
    if (here.ageAt(now) == WeatherAge.Expired) return null
    if (forecast.fetchedAt.toLocalDateTime(zone).date != now.toLocalDateTime(zone).date) return null

    val current = forecast.conditionsAt(now) ?: return null
    val place = state.here?.name?.let { " in $it" }.orEmpty()
    val adjective = current.sky.adjective()
    val day = forecast.dayAt(now)
    val detail = listOfNotNull(
        day?.let { "High ${degrees(it.high, units)}" },
        day?.let { "Low ${degrees(it.low, units)}" },
        dayLine(forecast, now, zone),
    )
    val high = day?.let { ", with a high of ${spokenDegrees(it.high, units)}" }.orEmpty()
    return MorningWeather(
        sky = current.sky,
        night = !current.isDay,
        headline = "${degrees(current.temperature, units)} and $adjective$place",
        detail = detail.joinToString(" · "),
        tip = weatherTip(forecast, now, zone),
        spoken = "It's ${spokenDegrees(current.temperature, units)} degrees and $adjective$place$high.",
    )
}

private fun Sky.adjective() = when (this) {
    Sky.CLEAR -> "clear"
    Sky.PARTLY_CLOUDY -> "bright"
    Sky.CLOUDY -> "cloudy"
    Sky.FOG -> "foggy"
    Sky.DRIZZLE -> "drizzly"
    Sky.RAIN -> "rainy"
    Sky.SNOW -> "snowy"
    Sky.STORM -> "stormy"
}

private fun whole(celsius: Double, units: Units): Int =
    (if (units == Units.FAHRENHEIT) celsius.toFahrenheit() else celsius).roundToInt()

private fun degrees(celsius: Double, units: Units) = "${whole(celsius, units)}°"

private fun spokenDegrees(celsius: Double, units: Units): String {
    val degrees = whole(celsius, units)
    return if (degrees < 0) "minus ${-degrees}" else "$degrees"
}
