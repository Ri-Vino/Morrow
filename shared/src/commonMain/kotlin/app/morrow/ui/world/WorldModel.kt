package app.morrow.ui.world

import app.morrow.domain.timeZoneOrNull
import app.morrow.places.City
import app.morrow.places.SavedCity
import app.morrow.theme.sunElevation
import app.morrow.ui.degrees
import app.morrow.ui.displayName
import app.morrow.weather.Forecast
import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.WeatherAge
import app.morrow.weather.WeatherState
import app.morrow.weather.conditionsAt
import app.morrow.weather.dayAt
import app.morrow.weather.forecastAt
import app.morrow.weather.weatherAge
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Everything the World tab shows, worked out from the saved cities, their weather and the time. */
data class WorldModel(
    /** "Your time 07:40 · London"; the screen sets it in capitals. */
    val header: String,
    val cards: List<WorldCard>,
    /** Under the cards: who the weather is from and how old it is, or why there's none. Null until the weather loads. */
    val footer: String? = null,
)

data class WorldCard(
    val id: Long,
    val name: String,
    val isHome: Boolean,
    val clock: String,
    val relative: String,
    /** Minutes since midnight in the city, for its sky. */
    val minute: Int,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    /** Null for a card that shows the time of day only. */
    val weather: CardWeather? = null,
)

/** A city's weather on its card. */
data class CardWeather(
    val sky: Sky,
    /** Whether it's night in the city, for the moon icon on a clear night. */
    val night: Boolean,
    /** "12° Rain". */
    val conditions: String,
    /** "H 14°  L 8°", for the day it is in the city; null when the forecast doesn't have that day. */
    val highLow: String?,
    /** "12° and rain", for screen readers. */
    val spoken: String,
)

/** What the sheet for one city shows. */
data class CitySheet(
    val id: Long,
    val name: String,
    /** "England, United Kingdom". */
    val place: String,
    val clock: String,
    val relative: String,
    val isHome: Boolean,
    /** "New alarm in New York time", or just "New alarm" for a city on the phone's own clock. */
    val newAlarm: String,
    /** "12° Rain", after the relative day; null without weather. */
    val conditions: String? = null,
    /** The hours after this one, up to 6; empty without weather. */
    val hours: List<SheetHour> = emptyList(),
    /** "High 14° · Low 8° · 80% chance of rain"; null without weather. */
    val day: String? = null,
)

/** One hour in a city's sheet, on the city's clock. */
data class SheetHour(
    /** "08". */
    val time: String,
    val sky: Sky,
    val night: Boolean,
    /** "13°". */
    val temperature: String,
    /** "08:00, 13°, rain". */
    val spoken: String,
)

/** [weather] is null while it loads; with weather off, or none recent enough for a city, its card shows the time only. */
fun worldModel(
    cities: List<SavedCity>,
    now: Instant,
    phoneZone: TimeZone,
    weather: WeatherState? = null,
    units: Units = Units.CELSIUS,
): WorldModel {
    val home = cities.firstOrNull { it.isHome }
    val header = buildString {
        append("Your time ").append(cityClock(now, phoneZone))
        if (home != null) append(" · ").append(home.city.name)
    }
    return WorldModel(
        header = header,
        cards = cities.mapIndexed { index, saved ->
            val zone = zoneOf(saved.city, phoneZone)
            val minute = cityMinute(now, zone)
            WorldCard(
                id = saved.id,
                name = saved.city.name,
                isHome = saved.isHome,
                clock = cityClock(now, zone),
                relative = relativeDay(now, zone, phoneZone),
                minute = minute,
                canMoveUp = index > 0,
                canMoveDown = index < cities.lastIndex,
                weather = shownForecast(weather, saved, now)?.let { cardWeather(it, now, minute, units) },
            )
        },
        footer = if (cities.isEmpty()) null else worldFooter(weather, cities.mapNotNull { shownForecast(weather, it, now) }, now),
    )
}

fun citySheet(saved: SavedCity, now: Instant, phoneZone: TimeZone, weather: WeatherState? = null, units: Units = Units.CELSIUS): CitySheet {
    val zone = zoneOf(saved.city, phoneZone)
    val forecast = shownForecast(weather, saved, now)
    return CitySheet(
        id = saved.id,
        name = saved.city.name,
        place = placeName(saved.city),
        clock = cityClock(now, zone),
        relative = relativeDay(now, zone, phoneZone),
        isHome = saved.isHome,
        newAlarm = if (zone == phoneZone) "New alarm" else "New alarm in ${saved.city.name} time",
        conditions = forecast?.conditionsAt(now)?.let { "${degrees(it.temperature, units)} ${it.sky.displayName}" },
        hours = forecast?.hours.orEmpty().filter { it.time > now }.take(SHEET_HOURS).map { hour ->
            val clockHour = hour.time.toLocalDateTime(zone).hour
            val time = clockHour.toString().padStart(2, '0')
            val temperature = degrees(hour.temperature, units)
            SheetHour(
                time = time,
                sky = hour.sky,
                night = sunElevation(clockHour * 60f) < 0,
                temperature = temperature,
                spoken = "$time:00, $temperature, ${hour.sky.displayName.lowercase()}",
            )
        },
        day = forecast?.dayAt(now)?.let { today ->
            "High ${degrees(today.high, units)} · Low ${degrees(today.low, units)}" +
                (today.rainChance?.let { " · $it% chance of rain" } ?: "")
        },
    )
}

/** "Texas, United States", or just the country when there is no region. */
fun placeName(city: City): String = listOfNotNull(city.region, city.country).filter { it.isNotBlank() }.joinToString(", ")

/** The city's zone, or the phone's if the id isn't one this phone knows. */
internal fun zoneOf(city: City, fallback: TimeZone): TimeZone = timeZoneOrNull(city.timeZone) ?: fallback

/**
 * The city's forecast if its card may show it: weather is on, the forecast is no more than a day old, and its hours
 * still reach [now], so an old reading is never shown as the weather now.
 */
private fun shownForecast(weather: WeatherState?, saved: SavedCity, now: Instant): Forecast? =
    weather?.takeIf { it.enabled }?.cities?.get(saved.id)?.forecastAt(now)?.takeIf { it.conditionsAt(now) != null }

private fun cardWeather(forecast: Forecast, now: Instant, minute: Int, units: Units): CardWeather? {
    val conditions = forecast.conditionsAt(now) ?: return null
    val temperature = degrees(conditions.temperature, units)
    return CardWeather(
        sky = conditions.sky,
        night = sunElevation(minute.toFloat()) < 0,
        conditions = "$temperature ${conditions.sky.displayName}",
        highLow = forecast.dayAt(now)?.let { day -> "H ${degrees(day.high, units)}  L ${degrees(day.low, units)}" },
        spoken = "$temperature and ${conditions.sky.displayName.lowercase()}",
    )
}

/** Says how old the cards' weather is, going by the oldest [shown] so it never sounds newer than it is. */
private fun worldFooter(weather: WeatherState?, shown: List<Forecast>, now: Instant): String? = when {
    weather == null -> null
    !weather.enabled -> "Weather is off, so cards show the time of day only."
    shown.isEmpty() -> "No recent weather, so cards show the time of day only."
    else -> when (val age = weatherAge(shown.minOf { it.fetchedAt }, now)) {
        is WeatherAge.Fresh -> "$ATTRIBUTION · updated " + if (age.minutes < 1) "just now" else "${age.minutes} min ago"
        is WeatherAge.Old -> "$ATTRIBUTION · from ${age.hours} hour${if (age.hours == 1L) "" else "s"} ago"
        WeatherAge.Expired -> null
    }
}

private const val ATTRIBUTION = "Weather by Open-Meteo.com"
private const val SHEET_HOURS = 6
