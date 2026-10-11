package app.morrow.weather

import app.morrow.alarms.FakeClock
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.FakeWeatherCacheDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.places.City
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.HttpResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryTest {

    private val start = Instant.parse("2026-10-11T06:00:00Z")
    private val clock = FakeClock(start)
    private val settings = MorrowSettings(InMemoryPreferences())
    private val catalog = CityCatalog {
        """
        2643743	London		England	United Kingdom	51.5085	-0.1257	Europe/London	8961989
        2644688	Leeds		England	United Kingdom	53.7965	-1.5479	Europe/London	455123
        5128581	New York City		New York	United States	40.7143	-74.0060	America/New_York	8804190
        1850147	Tokyo		Tokyo	Japan	35.6895	139.6917	Asia/Tokyo	9733276
        """.trimIndent()
    }
    private var phoneZone = "Europe/London"
    private val cities = CityRepository(FakeSavedCityDao(), catalog) { phoneZone }
    private val location = FakeLocationProvider()
    private val source = FakeWeatherSource { clock.now }
    private val cache = FakeWeatherCacheDao()

    private val tokyo = City(1850147, "Tokyo", "Tokyo", "Japan", 35.6895, 139.6917, "Asia/Tokyo", 9733276)
    private val newYork = City(5128581, "New York City", "New York", "United States", 40.7143, -74.0060, "America/New_York", 8804190)

    private val london = Coordinates(51.51, -0.13)
    private val leeds = Coordinates(53.8, -1.55)
    private val tokyoPlace = Coordinates(35.69, 139.69)
    private val newYorkPlace = Coordinates(40.71, -74.01)

    private fun TestScope.weather(source: WeatherSource = this@WeatherRepositoryTest.source) =
        WeatherRepository(settings, cities, catalog, location, source, cache, clock, { phoneZone }, backgroundScope)

    private suspend fun seedLondonAndTokyo() {
        cities.ensureSeeded()
        cities.add(tokyo)
    }

    @Test
    fun withWeatherOffNothingIsFetchedAndLocationIsNeverAsked() = runTest {
        seedLondonAndTokyo()
        settings.setWeatherEnabled(false)
        location.fix = leeds
        val http = FakeHttpClient.ok(ThreeForecastsJson)
        val weather = weather(OpenMeteoSource(http, clock))

        for (reason in RefreshReason.entries) weather.refresh(reason)
        weather.refreshCity(newYork)
        weather.refreshSoon(RefreshReason.START)
        weather.refreshCitySoon(newYork)
        runCurrent()

        assertEquals(emptyList(), http.urls)
        assertEquals(0, location.calls)
        assertEquals(WeatherState.Off, weather.forecasts.first())
        assertNull(settings.lastLocation.first())
    }

    @Test
    fun theFirstRefreshFetchesHereAndEveryCityInOneRequest() = runTest {
        seedLondonAndTokyo()
        location.fix = Coordinates(53.7997, -1.5492)

        weather(source).refresh(RefreshReason.START)

        assertEquals(listOf(listOf(leeds, london, tokyoPlace)), source.requests)
        assertEquals(setOf(leeds.key, london.key, tokyoPlace.key), cache.rows.value.keys)
        assertEquals(leeds, settings.lastLocation.first()?.coordinates)
    }

    @Test
    fun theRealSourceSendsOneRequestForManyPlaces() = runTest {
        seedLondonAndTokyo()
        location.fix = Coordinates(40.7143, -74.0060)
        val http = FakeHttpClient.ok(ThreeForecastsJson)

        weather(OpenMeteoSource(http, clock)).refresh(RefreshReason.START)

        assertEquals(1, http.urls.size)
        assertTrue(http.urls.single().contains("latitude=40.71,51.51,35.69&longitude=-74.01,-0.13,139.69"), http.urls.single())
        assertEquals(3, cache.rows.value.size)
    }

    @Test
    fun aFreshCacheIsLeftAlone() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        clock.now = start + 29.minutes
        weather.refresh(RefreshReason.START)
        weather.refresh(RefreshReason.WORLD)

        assertEquals(1, source.requests.size)
    }

    @Test
    fun onlyStalePlacesAreFetched() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        clock.now = start + 20.minutes
        cities.add(newYork)
        weather.refresh(RefreshReason.WORLD)
        assertEquals(listOf(newYorkPlace), source.requests.last())

        clock.now = start + 35.minutes
        weather.refresh(RefreshReason.START)
        assertEquals(listOf(london, tokyoPlace), source.requests.last())
        assertEquals(3, source.requests.size)
    }

    @Test
    fun refreshNowFetchesEverythingHoweverFresh() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        weather.refresh(RefreshReason.MANUAL)

        assertEquals(listOf(london, tokyoPlace), source.requests.last())
        assertEquals(2, source.requests.size)
    }

    @Test
    fun addingACityFetchesJustThatCity() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        cities.add(newYork)
        weather.refreshCity(newYork)

        assertEquals(listOf(newYorkPlace), source.requests.last())
        assertEquals(1, location.calls, "only the first refresh asks for the location")
    }

    @Test
    fun a429BacksOffForAnHour() = runTest {
        seedLondonAndTokyo()
        val http = FakeHttpClient { HttpResponse(429, "") }
        val weather = weather(OpenMeteoSource(http, clock))

        weather.refresh(RefreshReason.START)
        assertEquals(1, http.urls.size)
        assertEquals(start + 1.hours, settings.weatherBackoffUntil.first())

        clock.now = start + 59.minutes
        weather.refresh(RefreshReason.START)
        weather.refresh(RefreshReason.MANUAL)
        weather.refreshCity(newYork)
        assertEquals(1, http.urls.size)
        assertEquals(1, location.calls, "no location while backing off either")

        clock.now = start + 61.minutes
        weather.refresh(RefreshReason.START)
        assertEquals(2, http.urls.size)
    }

    @Test
    fun aFailedFetchKeepsTheCache() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)
        val before = cache.rows.value

        clock.now = start + 40.minutes
        source.failure = FetchException("offline")
        weather.refresh(RefreshReason.START)

        assertEquals(2, source.requests.size)
        assertEquals(before, cache.rows.value)
        assertNull(settings.weatherBackoffUntil.first())
        val state = weather.forecasts.first()
        assertEquals(WeatherAge.Fresh(40), state.hereWeather?.age)
        assertEquals(10.0, state.cities.getValue(tokyo.id).forecast?.now?.temperature)
    }

    @Test
    fun cacheAgesAreFreshThenOldThenExpired() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        clock.now = start + 10.minutes
        assertEquals(WeatherAge.Fresh(10), weather.forecasts.first().cities.getValue(tokyo.id).age)
        clock.now = start + 5.hours
        assertEquals(WeatherAge.Old(5), weather.forecasts.first().cities.getValue(tokyo.id).age)
        clock.now = start + 2.days
        val expired = weather.forecasts.first().cities.getValue(tokyo.id)
        assertEquals(WeatherAge.Expired, expired.age)
        assertEquals(WeatherAge.Old(3), expired.ageAt(start + 3.hours + 5.minutes))
    }

    @Test
    fun aCityNeverFetchedHasNoForecast() = runTest {
        seedLondonAndTokyo()

        val state = weather().forecasts.first()

        assertTrue(state.enabled)
        assertNull(state.cities.getValue(tokyo.id).forecast)
        assertEquals(WeatherAge.Expired, state.cities.getValue(tokyo.id).age)
    }

    @Test
    fun hereIsTheCurrentFixNamedByTheNearestCity() = runTest {
        seedLondonAndTokyo()
        location.fix = Coordinates(53.7997, -1.5492)
        val weather = weather()

        weather.refresh(RefreshReason.START)

        assertEquals(HerePlace(leeds, "Leeds", HereSource.LOCATION), weather.here.first())
        assertEquals(leeds, weather.forecasts.first().hereWeather?.forecast?.place)
    }

    @Test
    fun withLocationDeniedTheLastFixIsUsed() = runTest {
        seedLondonAndTokyo()
        settings.saveLastLocation(Coordinates(53.7997, -1.5492), start - 1.days)
        val weather = weather()

        weather.refresh(RefreshReason.START)

        assertEquals(1, location.calls)
        assertEquals(HerePlace(leeds, "Leeds", HereSource.LOCATION), weather.here.first())
        assertEquals(listOf(leeds, london, tokyoPlace), source.requests.single())
    }

    @Test
    fun withNoFixAtAllTheHomeCityIsUsed() = runTest {
        cities.ensureSeeded()
        cities.add(tokyo)
        cities.makeHome(tokyo.id)
        val weather = weather()

        weather.refresh(RefreshReason.START)

        assertEquals(HerePlace(tokyoPlace, "Tokyo", HereSource.HOME_CITY), weather.here.first())
        assertEquals(listOf(tokyoPlace, london), source.requests.single())
    }

    @Test
    fun withNoFixAndNoHomeCityThePhoneZonesCityIsUsed() = runTest {
        phoneZone = "America/New_York"
        val weather = weather()

        weather.refresh(RefreshReason.START)

        assertEquals(HerePlace(newYorkPlace, "New York City", HereSource.ZONE_CITY), weather.here.first())
        assertEquals(listOf(listOf(newYorkPlace)), source.requests)
    }

    @Test
    fun homeCityModeNeverAsksForTheLocation() = runTest {
        seedLondonAndTokyo()
        settings.setWeatherSource(WeatherPlaceMode.HOME)
        settings.saveLastLocation(leeds, start)
        location.fix = leeds
        val weather = weather()

        weather.refresh(RefreshReason.START)

        assertEquals(0, location.calls)
        assertEquals(HerePlace(london, "London", HereSource.HOME_CITY), weather.here.first())
        assertEquals(listOf(london, tokyoPlace), source.requests.single())
    }

    @Test
    fun placesNoLongerAskedAboutLeaveTheCache() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        cities.remove(tokyo.id)
        weather.refresh(RefreshReason.START)

        assertEquals(setOf(london.key), cache.rows.value.keys)
    }

    @Test
    fun onlyOneRefreshRunsAtATime() = runTest {
        seedLondonAndTokyo()
        val gate = CompletableDeferred<Unit>()
        val slow = object : WeatherSource {
            var calls = 0
            override suspend fun forecast(places: List<Coordinates>): List<Forecast> {
                calls++
                gate.await()
                return places.map { forecastFor(it, clock.now) }
            }
        }
        val weather = weather(slow)

        launch { weather.refresh(RefreshReason.START) }
        runCurrent()
        weather.refresh(RefreshReason.WORLD)
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, slow.calls)
        assertEquals(2, cache.rows.value.size)
    }

    @Test
    fun turningWeatherOffHidesTheCache() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)
        assertTrue(weather.forecasts.first().enabled)

        settings.setWeatherEnabled(false)

        assertEquals(WeatherState.Off, weather.forecasts.first())
        assertFalse(cache.rows.value.isEmpty())
    }
}
