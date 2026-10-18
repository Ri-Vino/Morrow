package app.morrow.weather

import app.morrow.platform.HttpException
import app.morrow.platform.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class OpenMeteoSourceTest {
    private val now = Instant.parse("2026-10-11T06:20:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val london = Coordinates(51.507351, -0.127758)
    private val newYork = Coordinates(40.712776, -74.005974)
    private val tokyo = Coordinates(35.689487, 139.691711)

    @Test
    fun readsOnePlace() = runTest {
        val forecast = OpenMeteoSource(FakeHttpClient.ok(OneForecastJson), clock).forecast(listOf(london)).single()

        assertEquals(Coordinates(51.51, -0.13), forecast.place)
        assertEquals("Europe/London", forecast.timeZone)
        assertEquals(now, forecast.fetchedAt)
        assertEquals(CurrentWeather(temperature = 11.4, sky = Sky.RAIN, isDay = true, wind = 24.5), forecast.now)
        assertEquals(DayWeather(high = 14.2, low = 7.9, rainChance = 80), forecast.today)
        assertEquals(DayWeather(high = 15.0, low = 8.4, rainChance = 20), forecast.tomorrow)
    }

    @Test
    fun theNext12HoursStartWithTheCurrentHour() = runTest {
        val hours = OpenMeteoSource(FakeHttpClient.ok(OneForecastJson), clock).forecast(listOf(london)).single().hours

        assertEquals(12, hours.size)
        // 07:15 in London is in the 07:00 hour, index 7, which is 06:00 UTC in summer time.
        assertEquals(HourWeather(Instant.parse("2026-10-11T06:00:00Z"), 8.7, Sky.RAIN, rainChance = 7, wind = 7.0), hours.first())
        assertEquals(HourWeather(Instant.parse("2026-10-11T17:00:00Z"), 9.8, Sky.CLOUDY, rainChance = 18, wind = 18.0), hours.last())
        assertEquals((7..18).toList(), hours.map { it.rainChance })
        assertEquals(hours.sortedBy { it.time }, hours)
    }

    @Test
    fun readsSeveralPlacesInTheOrderAsked() = runTest {
        val forecasts = OpenMeteoSource(FakeHttpClient.ok(ThreeForecastsJson), clock).forecast(listOf(london, newYork, tokyo))

        assertEquals(listOf(london.rounded(), newYork.rounded(), tokyo.rounded()), forecasts.map { it.place })
        assertEquals(listOf("Europe/London", "America/New_York", "Asia/Tokyo"), forecasts.map { it.timeZone })

        val (_, ny, tk) = forecasts
        assertEquals(CurrentWeather(9.0, Sky.CLEAR, isDay = false), ny.now)
        assertEquals(DayWeather(17.5, 6.2, 3), ny.today)
        // 02:15 in New York: from the 02:00 hour, 06:00 UTC.
        assertEquals(Instant.parse("2026-10-11T06:00:00Z"), ny.hours.first().time)
        assertEquals(2, ny.hours.first().rainChance)

        assertEquals(CurrentWeather(22.8, Sky.STORM, isDay = true), tk.now)
        // 15:15 in Tokyo: the 15:00 hour is 06:00 UTC, and the storm turns to showers at index 20.
        assertEquals(Instant.parse("2026-10-11T06:00:00Z"), tk.hours.first().time)
        assertEquals(List(5) { Sky.STORM } + List(7) { Sky.RAIN }, tk.hours.map { it.sky })
        forecasts.forEach { assertEquals(12, it.hours.size) }
    }

    @Test
    fun asksOnceForEveryPlaceWithRoundedCoordinatesAndExactlyTheSpecFields() = runTest {
        val http = FakeHttpClient.ok(ThreeForecastsJson)
        OpenMeteoSource(http, clock).forecast(listOf(london, newYork, tokyo))

        assertEquals(
            listOf(
                "https://api.open-meteo.com/v1/forecast?latitude=51.51,40.71,35.69&longitude=-0.13,-74.01,139.69" +
                    "&current=temperature_2m,weather_code,is_day,wind_speed_10m" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&forecast_days=2&timezone=auto",
            ),
            http.urls,
        )
    }

    @Test
    fun theUrlNeverCarriesMoreThanTwoDecimals() = runTest {
        val http = FakeHttpClient { HttpResponse(500, "") }
        val precise = listOf(Coordinates(51.50735123456, -0.1277583), Coordinates(0.1 + 0.2, 1.0 / 3), Coordinates(-89.999999, 179.9999))
        assertFailsWith<FetchException> { OpenMeteoSource(http, clock).forecast(precise) }

        val url = http.urls.single()
        assertFalse(Regex("""\d\.\d{3,}""").containsMatchIn(url), url)
        assertTrue("latitude=51.51,0.30,-90.00&longitude=-0.13,0.33,180.00" in url, url)
    }

    @Test
    fun placesThatRoundTogetherAreAskedForOnce() = runTest {
        val http = FakeHttpClient.ok(OneForecastJson)
        val forecasts = OpenMeteoSource(http, clock).forecast(listOf(london, Coordinates(51.509, -0.131)))

        assertEquals(1, forecasts.size)
        assertTrue("latitude=51.51&longitude=-0.13&" in http.urls.single())
    }

    @Test
    fun noPlacesMeansNoRequest() = runTest {
        val http = FakeHttpClient.ok(OneForecastJson)
        assertEquals(emptyList(), OpenMeteoSource(http, clock).forecast(emptyList()))
        assertEquals(emptyList(), http.urls)
    }

    @Test
    fun aMalformedReplyIsAFetchException() = runTest {
        for (body in listOf("<html>Bad gateway</html>", "", "{}", """{"latitude": 51.5}""", OneForecastJson.dropLast(40))) {
            assertFailsWith<FetchException>(body.take(30)) {
                OpenMeteoSource(FakeHttpClient.ok(body), clock).forecast(listOf(london))
            }
        }
    }

    @Test
    fun aReplyWithTheWrongNumberOfPlacesIsAFetchException() = runTest {
        assertFailsWith<FetchException> {
            OpenMeteoSource(FakeHttpClient.ok(OneForecastJson), clock).forecast(listOf(london, tokyo))
        }
    }

    @Test
    fun aTooManyRequestsReplyIsRateLimited() = runTest {
        val error = assertFailsWith<FetchException> {
            OpenMeteoSource(FakeHttpClient { HttpResponse(429, """{"error":true,"reason":"Too many requests"}""") }, clock)
                .forecast(listOf(london))
        }
        assertTrue(error.rateLimited)
    }

    @Test
    fun aServerErrorOrNoAnswerIsAFetchExceptionThatIsNotRateLimited() = runTest {
        val serverError = assertFailsWith<FetchException> {
            OpenMeteoSource(FakeHttpClient { HttpResponse(400, """{"error":true,"reason":"Latitude must be in range"}""") }, clock)
                .forecast(listOf(london))
        }
        assertFalse(serverError.rateLimited)

        val offline = assertFailsWith<FetchException> {
            OpenMeteoSource(FakeHttpClient { throw HttpException("offline") }, clock).forecast(listOf(london))
        }
        assertFalse(offline.rateLimited)
    }

    @Test
    fun aForecastSurvivesAJsonRoundTripForTheCache() = runTest {
        val forecasts = OpenMeteoSource(FakeHttpClient.ok(ThreeForecastsJson), clock).forecast(listOf(london, newYork, tokyo))
        for (forecast in forecasts) {
            assertEquals(forecast, Json.decodeFromString<Forecast>(Json.encodeToString(forecast)))
        }
    }
}
