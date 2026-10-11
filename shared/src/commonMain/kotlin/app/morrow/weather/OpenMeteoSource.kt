package app.morrow.weather

import app.morrow.platform.HttpClient
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Clock
import kotlin.time.Instant

/** Forecasts from api.open-meteo.com. Sends only rounded coordinates; temperatures come back in °C. */
class OpenMeteoSource(private val http: HttpClient, private val clock: Clock) : WeatherSource {

    override suspend fun forecast(places: List<Coordinates>): List<Forecast> {
        val asked = places.map { it.rounded() }.distinct()
        if (asked.isEmpty()) return emptyList()
        val reply = http.getJson(forecastUrl(asked))
        val fetchedAt = clock.now()
        // One place comes back as an object, several as an array in the order asked.
        val dtos = readOrFail {
            if (reply is JsonArray) {
                OpenMeteoJson.decodeFromJsonElement<List<ForecastDto>>(reply)
            } else {
                listOf(OpenMeteoJson.decodeFromJsonElement<ForecastDto>(reply))
            }
        }
        if (dtos.size != asked.size) throw FetchException("Open-Meteo sent ${dtos.size} forecasts for ${asked.size} places")
        return asked.zip(dtos) { place, dto -> readOrFail { dto.toForecast(place, fetchedAt) } }
    }

    internal companion object {
        const val FIELDS = "current=temperature_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=2&timezone=auto"

        fun forecastUrl(places: List<Coordinates>): String {
            val latitudes = places.joinToString(",") { it.latitude.twoDecimals() }
            val longitudes = places.joinToString(",") { it.longitude.twoDecimals() }
            return "https://api.open-meteo.com/v1/forecast?latitude=$latitudes&longitude=$longitudes&$FIELDS"
        }

        const val HOURS = 12
    }
}

private fun ForecastDto.toForecast(place: Coordinates, fetchedAt: Instant): Forecast {
    val zone = try {
        TimeZone.of(timezone)
    } catch (_: IllegalTimeZoneException) {
        FixedOffsetTimeZone(UtcOffset(seconds = utcOffsetSeconds))
    }
    val currentTime = LocalDateTime.parse(current.time)
    val thisHour = LocalDateTime(currentTime.date, LocalTime(currentTime.hour, 0))

    val hours = hourly.time.indices
        .filter { LocalDateTime.parse(hourly.time[it]) >= thisHour }
        .mapNotNull { i ->
            val temperature = hourly.temperature.getOrNull(i) ?: return@mapNotNull null
            val code = hourly.weatherCode.getOrNull(i) ?: return@mapNotNull null
            HourWeather(
                time = LocalDateTime.parse(hourly.time[i]).toInstant(zone),
                temperature = temperature,
                sky = skyForWmo(code),
                rainChance = hourly.rainChance.getOrNull(i),
            )
        }
        .take(OpenMeteoSource.HOURS)

    val todayIndex = daily.time.indexOfFirst { LocalDate.parse(it) == currentTime.date }.takeIf { it >= 0 } ?: 0
    return Forecast(
        place = place,
        timeZone = timezone,
        fetchedAt = fetchedAt,
        now = CurrentWeather(
            temperature = current.temperature ?: throw FetchException("No current temperature"),
            sky = skyForWmo(current.weatherCode ?: throw FetchException("No current weather code")),
            isDay = current.isDay != 0,
        ),
        today = DayWeather(
            high = daily.high.getOrNull(todayIndex) ?: throw FetchException("No high for today"),
            low = daily.low.getOrNull(todayIndex) ?: throw FetchException("No low for today"),
            rainChance = daily.rainChance.getOrNull(todayIndex),
        ),
        hours = hours,
    )
}

@Serializable
private class ForecastDto(
    val timezone: String,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
)

/** [time] is the place's local time, like "2026-10-11T07:15". */
@Serializable
private class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
private class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("precipitation_probability") val rainChance: List<Int?> = emptyList(),
)

@Serializable
private class DailyDto(
    val time: List<String>,
    @SerialName("temperature_2m_max") val high: List<Double?>,
    @SerialName("temperature_2m_min") val low: List<Double?>,
    @SerialName("precipitation_probability_max") val rainChance: List<Int?> = emptyList(),
)
