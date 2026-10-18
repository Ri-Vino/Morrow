package app.morrow.weather

import app.morrow.platform.LocationProvider
import kotlin.time.Duration
import kotlin.time.Instant

/** A location that's [fix], or none, as if denied. Counts every time it's asked. */
class FakeLocationProvider(var fix: Coordinates? = null) : LocationProvider {
    var calls = 0
        private set

    override val allowed: Boolean
        get() {
            calls++
            return fix != null
        }

    override suspend fun current(timeout: Duration): Coordinates? {
        calls++
        return fix?.rounded()
    }
}

/** Answers with a 10 °C forecast per place, or throws [failure]. Remembers the places of each request. */
class FakeWeatherSource(private val now: () -> Instant) : WeatherSource {
    val requests = mutableListOf<List<Coordinates>>()
    var failure: FetchException? = null

    override suspend fun forecast(places: List<Coordinates>): List<Forecast> {
        val asked = places.map { it.rounded() }.distinct()
        requests += asked
        failure?.let { throw it }
        return asked.map { forecastFor(it, now()) }
    }
}

fun forecastFor(place: Coordinates, at: Instant, temperature: Double = 10.0, sky: Sky = Sky.CLEAR, wind: Double? = null) = Forecast(
    place = place,
    timeZone = "Europe/London",
    fetchedAt = at,
    now = CurrentWeather(temperature, sky, isDay = true, wind = wind),
    today = DayWeather(high = temperature + 4, low = temperature - 3, rainChance = 10),
    hours = listOf(HourWeather(at, temperature, sky, rainChance = 10, wind = wind)),
)
