package app.morrow.weather

import app.morrow.ui.hhmm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A day with a high of at least this many °C is very hot. */
internal const val VERY_HOT_HIGH = 30.0

/** A day with a high of at most this many °C is cold. */
internal const val COLD_HIGH = 8.0

/** At or below this many °C rain freezes where it lands, and the day is cold whatever its high. */
internal const val FREEZING = 0.0

/** An hour with at least this chance of rain, in percent, counts as wet even when its sky looks dry. */
internal const val LIKELY_RAIN = 60

/**
 * Pip's tip for the day ahead, or null when there is nothing worth saying. The first that applies wins:
 * storm, snow or ice, rain now or later, very hot, cold, sunny. [now] and [zone] are the phone's, which is how
 * "later" and the part of the day are told.
 */
fun weatherTip(forecast: Forecast, now: Instant, zone: TimeZone): String? {
    val hours = ahead(forecast, now).ifEmpty { listOf(forecast.currentHour(now)) }
    val current = forecast.conditionsAt(now) ?: forecast.now

    hours.indexOfFirst { it.precipitation() == Precipitation.STORM }.takeIf { it >= 0 }?.let { storm ->
        if (storm > 0) return "Storms are forecast ${partOfDay(hours[storm], zone)}. Try not to get caught out."
        val calm = hours.firstOrNull { it.precipitation() != Precipitation.STORM } ?: return "Stay in if you can. It's stormy for a while yet."
        return "Stay in if you can. It calms down ${partOfDay(calm, zone)}."
    }
    if (hours.any { it.precipitation() == Precipitation.SNOW }) return "Wrap up warm, and mind the pavements."
    if (hours.any { it.precipitation() == Precipitation.RAIN && it.temperature <= FREEZING }) return "It could be icy underfoot. Mind the pavements."
    hours.indexOfFirst { it.precipitation() == Precipitation.RAIN }.takeIf { it >= 0 }?.let { rain ->
        if (rain > 0) return "Take an umbrella. Rain's due ${partOfDay(hours[rain], zone)}."
        val dry = hours.firstOrNull { it.precipitation() == null } ?: return "Take an umbrella. It isn't going anywhere soon."
        return "Take an umbrella. It dries up ${partOfDay(dry, zone)}."
    }
    val high = forecast.dayAt(now)?.high
    if (high != null && high >= VERY_HOT_HIGH) return "Hot one today. Water, shade and sun cream."
    if ((high != null && high <= COLD_HIGH) || current.temperature <= FREEZING) return "Properly cold today. Coat, hat and gloves."
    return when (current.sky) {
        Sky.CLEAR -> "A lovely clear one. Get outside if you can."
        Sky.PARTLY_CLOUDY -> "Bright spells today. Sunglasses in the bag."
        else -> null
    }
}

/**
 * What the rest of the day looks like, such as "Rain until about 11:00" or "Dry all day", with times on the phone's
 * clock. Null when the forecast's hours have all gone by.
 */
fun dayLine(forecast: Forecast, now: Instant, zone: TimeZone): String? {
    val hours = ahead(forecast, now).ifEmpty { return null }
    val first = hours.first()
    if (first.precipitation() != null) {
        val dry = hours.firstOrNull { it.precipitation() == null } ?: return "${first.spell()} most of the day"
        return "${first.spell()} until about ${clock(dry, zone)}"
    }
    if (first.sky == Sky.FOG) {
        val lifted = hours.firstOrNull { it.sky != Sky.FOG } ?: return "Fog most of the day"
        return "Fog lifting by ${clock(lifted, zone)}"
    }
    hours.firstOrNull { it.precipitation() != null }?.let { return "${it.spell()} from about ${clock(it, zone)}" }
    return if (first.sky == Sky.CLOUDY) "Dry under grey skies" else "Dry all day"
}

private enum class Precipitation { STORM, SNOW, RAIN }

private fun HourWeather.precipitation(): Precipitation? = when {
    sky == Sky.STORM -> Precipitation.STORM
    sky == Sky.SNOW -> Precipitation.SNOW
    sky == Sky.RAIN || sky == Sky.DRIZZLE || (rainChance ?: 0) >= LIKELY_RAIN -> Precipitation.RAIN
    else -> null
}

private fun HourWeather.spell(): String = when {
    precipitation() == Precipitation.STORM -> "Storms"
    precipitation() == Precipitation.SNOW -> "Snow"
    sky == Sky.DRIZZLE -> "Drizzle"
    else -> "Rain"
}

/** The hour [now] is in and those after it, which is all that's still to come. */
private fun ahead(forecast: Forecast, now: Instant): List<HourWeather> = forecast.hours.filter { it.time + 1.hours > now }

/** The current reading as an hour, for a forecast whose hours have all gone by. */
private fun Forecast.currentHour(now: Instant) = HourWeather(now, this.now.temperature, this.now.sky, rainChance = null)

private fun clock(hour: HourWeather, zone: TimeZone): String {
    val local = hour.time.toLocalDateTime(zone)
    return hhmm(local.hour, local.minute)
}

private fun partOfDay(hour: HourWeather, zone: TimeZone): String = when (hour.time.toLocalDateTime(zone).hour) {
    in 0..11 -> "this morning"
    in 12..14 -> "after lunch"
    in 15..17 -> "this afternoon"
    else -> "this evening"
}
