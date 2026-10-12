package app.morrow.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ConditionsTest {
    private val place = Coordinates(51.51, -0.13)
    private val fetchedAt = Instant.parse("2026-10-11T07:40:00Z")
    private val firstHour = Instant.parse("2026-10-11T07:00:00Z")

    private val forecast = Forecast(
        place = place,
        timeZone = "Europe/London",
        fetchedAt = fetchedAt,
        now = CurrentWeather(12.4, Sky.RAIN, isDay = true),
        today = DayWeather(high = 14.0, low = 8.0, rainChance = 80),
        hours = List(12) { i -> HourWeather(firstHour + i.hours, 10.0 + i, if (i < 4) Sky.RAIN else Sky.PARTLY_CLOUDY, 50) },
    )

    @Test
    fun withinTheHourItWasFetchedTheCurrentConditionsStand() {
        assertEquals(CurrentWeather(12.4, Sky.RAIN, isDay = true), forecast.conditionsAt(fetchedAt))
        assertEquals(CurrentWeather(12.4, Sky.RAIN, isDay = true), forecast.conditionsAt(fetchedAt + 19.minutes))
    }

    @Test
    fun laterTheForecastForTheHourItIsNowTakesOver() {
        assertEquals(CurrentWeather(12.0, Sky.RAIN, isDay = true), forecast.conditionsAt(Instant.parse("2026-10-11T09:00:00Z")))
        assertEquals(CurrentWeather(15.0, Sky.PARTLY_CLOUDY, isDay = true), forecast.conditionsAt(Instant.parse("2026-10-11T12:59:00Z")))
    }

    @Test
    fun pastTheLastForecastHourThereIsNothingToShow() {
        assertNull(forecast.conditionsAt(Instant.parse("2026-10-11T19:00:00Z")))
    }

    @Test
    fun aClockSetBeforeTheFetchKeepsTheFetchedConditions() {
        assertEquals(CurrentWeather(12.4, Sky.RAIN, isDay = true), forecast.conditionsAt(fetchedAt - 2.hours))
    }

    @Test
    fun aForecastShowsForADayAndNoLonger() {
        val weather = PlaceWeather(place, forecast, WeatherAge.Fresh(0))
        assertEquals(forecast, weather.forecastAt(fetchedAt + 5.minutes))
        assertEquals(forecast, weather.forecastAt(fetchedAt + 24.hours))
        assertNull(weather.forecastAt(fetchedAt + 24.hours + 1.minutes))
        assertNull(PlaceWeather(place, forecast = null, WeatherAge.Expired).forecastAt(fetchedAt))
    }
}
