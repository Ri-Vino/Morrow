package app.morrow.weather

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The weather for one place. Temperatures are always °C; see [toFahrenheit]. */
@Serializable
data class Forecast(
    /** The rounded place that was asked for, which is also its cache key. */
    val place: Coordinates,
    /** The place's IANA time zone, as the provider reports it. */
    val timeZone: String,
    val fetchedAt: Instant,
    val now: CurrentWeather,
    /** The day it was fetched on, in the place's own time zone. Use [dayAt] for the day it is now. */
    val today: DayWeather,
    /** The day after [today], for a forecast still shown after the place's midnight; null if unknown. */
    val tomorrow: DayWeather? = null,
    /** Up to 12 hours, starting with the current hour. */
    val hours: List<HourWeather>,
)

/** [wind] is the wind speed in km/h, or null for a forecast cached before it was asked for. */
@Serializable
data class CurrentWeather(val temperature: Double, val sky: Sky, val isDay: Boolean, val wind: Double? = null)

/** One day in the place's own time zone. [rainChance] is the highest chance of rain in the day, in percent, or null if unknown. */
@Serializable
data class DayWeather(val high: Double, val low: Double, val rainChance: Int?)

/** One hour, starting at [time]. [rainChance] is in percent, or null if unknown; [wind] is in km/h, or null if unknown. */
@Serializable
data class HourWeather(val time: Instant, val temperature: Double, val sky: Sky, val rainChance: Int?, val wind: Double? = null)
