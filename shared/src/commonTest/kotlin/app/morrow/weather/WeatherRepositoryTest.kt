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
import app.morrow.platform.LocationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

        clock.now = start + 6.minutes
        weather.refresh(RefreshReason.MANUAL)

        assertEquals(listOf(london, tokyoPlace), source.requests.last())
        assertEquals(2, source.requests.size)
    }

    @Test
    fun refreshNowLeavesWhatWasFetchedInTheLastFiveMinutes() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        clock.now = start + 4.minutes
        weather.refresh(RefreshReason.MANUAL)
        weather.refresh(RefreshReason.MANUAL)

        assertEquals(1, source.requests.size)
    }

    @Test
    fun eachForecastIsDecodedOnceForEveryScreen() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        val first = weather.forecasts.first()
        val again = weather.forecasts.first()

        assertTrue(first.cities.getValue(tokyo.id).forecast === again.cities.getValue(tokyo.id).forecast, "decoded once")
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

    /**
     * A phone that finds [fix] in [fixTime], or after the [timeout] it is given gives up without one, as the real
     * one does. Answers requests in [requestTime]. Remembers the timeouts it was given.
     */
    private inner class Slow(val fixTime: Duration, val requestTime: Duration, val fix: Coordinates = leeds) : LocationProvider, WeatherSource {
        val timeouts = mutableListOf<Duration>()
        val requests = mutableListOf<List<Coordinates>>()
        override val allowed = true

        override suspend fun current(timeout: Duration): Coordinates? {
            timeouts += timeout
            if (fixTime > timeout) {
                delay(timeout)
                return null
            }
            delay(fixTime)
            return fix.rounded()
        }

        override suspend fun forecast(places: List<Coordinates>): List<Forecast> {
            requests += places
            delay(requestTime)
            return places.map { forecastFor(it, clock.now) }
        }
    }

    private fun TestScope.weatherWith(slow: Slow) =
        WeatherRepository(settings, cities, catalog, slow, slow, cache, clock, { phoneZone }, backgroundScope)

    @Test
    fun theMorningFixGetsTwoOfItsFiveSeconds() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 1.seconds)

        weatherWith(slow).refresh(RefreshReason.MORNING)

        assertEquals(listOf(2.seconds), slow.timeouts)
    }

    @Test
    fun aMorningFixAndRequestWithinFiveSecondsBothFinish() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 3.seconds)

        weatherWith(slow).refresh(RefreshReason.MORNING)

        assertEquals(setOf(leeds.key, london.key, tokyoPlace.key), cache.rows.value.keys)
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun aMorningRefreshStartedDuringTheGamesIsAlreadyDoneWhenMorningOpens() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 3.seconds)
        val weather = weatherWith(slow)

        weather.prepareMorning()
        delay(30.seconds)
        val before = currentTime
        val stored = weather.refreshForMorning()

        assertEquals(before, currentTime, "nothing left to wait for")
        assertEquals(clock.now, stored)
        assertEquals(listOf(2.seconds), slow.timeouts, "the location was found once")
    }

    @Test
    fun aMorningRefreshStillRunningIsWaitedForNotStartedAgain() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 3.seconds)
        val weather = weatherWith(slow)

        weather.prepareMorning()
        delay(1.seconds)
        weather.refreshForMorning()

        assertEquals(4_000L, currentTime)
        assertEquals(listOf(2.seconds), slow.timeouts)
    }

    @Test
    fun aMorningRefreshStartedLongAgoIsNotTakenUp() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 1.seconds)
        val weather = weatherWith(slow)

        weather.prepareMorning()
        delay(10.seconds)
        clock.now = start + 31.minutes
        weather.refreshForMorning()

        assertEquals(listOf(2.seconds, 2.seconds), slow.timeouts)
    }

    @Test
    fun aMorningRefreshStartedAfterASnoozeIsStillTakenUp() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 1.seconds)
        val weather = weatherWith(slow)

        weather.prepareMorning()
        delay(10.seconds)
        clock.now = start + 25.minutes
        val before = currentTime
        weather.refreshForMorning()

        assertEquals(before, currentTime, "nothing left to wait for")
        assertEquals(listOf(2.seconds), slow.timeouts)
    }

    @Test
    fun preparingAgainWhileAMorningRefreshRunsKeepsIt() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 3.seconds)
        val weather = weatherWith(slow)

        weather.prepareMorning()
        delay(1.seconds)
        weather.prepareMorning()
        delay(30.seconds)

        assertEquals(listOf(2.seconds), slow.timeouts, "the location was found once")
    }

    @Test
    fun aMorningRefreshPreparedAgainUsesTheCacheItFilled() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 3.seconds)
        val weather = weatherWith(slow)

        // The alarm rings, is snoozed for ten minutes, then rings again.
        weather.prepareMorning()
        delay(30.seconds)
        clock.now = start + 10.minutes
        weather.prepareMorning()
        delay(30.seconds)

        assertEquals(1, slow.requests.size, "every place was still fresh")
    }

    @Test
    fun aPreparedMorningRefreshIsTakenUpOnce() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 1.seconds)
        val weather = weatherWith(slow)

        weather.prepareMorning()
        weather.refreshForMorning()
        weather.refreshForMorning()

        assertEquals(listOf(2.seconds, 2.seconds), slow.timeouts)
    }

    @Test
    fun aMorningFixSlowerThanItsShareFallsBackToTheHomeCityAndStillLoads() = runTest {
        cities.ensureSeeded()
        cities.add(tokyo)
        cities.makeHome(tokyo.id)
        val slow = Slow(fixTime = 6.seconds, requestTime = 2.seconds)
        val weather = weatherWith(slow)

        weather.refresh(RefreshReason.MORNING)

        assertEquals(HerePlace(tokyoPlace, "Tokyo", HereSource.HOME_CITY), weather.here.first())
        assertEquals(setOf(tokyoPlace.key, london.key), cache.rows.value.keys)
        assertNull(settings.lastLocation.first())
        assertEquals(4_000L, currentTime, "2 s for the fix to give up, then the 2 s request")
    }

    @Test
    fun aMorningFixSlowerThanItsShareFallsBackToTheLastFixAndStillLoads() = runTest {
        seedLondonAndTokyo()
        settings.saveLastLocation(Coordinates(53.7997, -1.5492), start - 1.days)
        val slow = Slow(fixTime = 6.seconds, requestTime = 2.seconds)
        val weather = weatherWith(slow)

        weather.refresh(RefreshReason.MORNING)

        assertEquals(HerePlace(leeds, "Leeds", HereSource.LOCATION), weather.here.first())
        assertEquals(setOf(leeds.key, london.key, tokyoPlace.key), cache.rows.value.keys)
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun aMorningRequestSlowerThanWhatIsLeftGivesUpAtFiveSeconds() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 4.5.seconds)

        weatherWith(slow).refresh(RefreshReason.MORNING)

        assertTrue(cache.rows.value.isEmpty(), "1 s for the fix and 4.5 s for the request is over the 5 s")
        assertNull(settings.lastLocation.first(), "the fix waits for its weather, so it isn't kept when the request is given up on")
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun aMorningFixInANewPlaceWhoseRequestRunsOutLeavesTheLastPlacesWeatherShowing() = runTest {
        seedLondonAndTokyo()
        settings.saveLastLocation(london, start - 1.hours)
        val weather = weather()
        weather.refresh(RefreshReason.START)
        val before = cache.rows.value
        clock.now = start + 1.hours

        // The phone has moved to Leeds, and the request takes longer than Morning's 5 s.
        weatherWith(Slow(fixTime = 1.seconds, requestTime = 10.seconds)).refresh(RefreshReason.MORNING)

        assertEquals(before, cache.rows.value)
        assertEquals(HerePlace(london, "London", HereSource.LOCATION), weather.here.first())
        assertEquals(london, weather.forecasts.first().hereWeather?.forecast?.place)
    }

    @Test
    fun aFailedRequestLeavesTheCacheOfPlacesNoLongerAskedAbout() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)
        cities.remove(tokyo.id)
        clock.now = start + 1.hours
        source.failure = FetchException("offline")

        weather.refresh(RefreshReason.START)

        assertEquals(setOf(london.key, tokyoPlace.key), cache.rows.value.keys)
    }

    @Test
    fun aRefreshSaysWhenTheForecastItStoredForHereWasFetched() = runTest {
        seedLondonAndTokyo()
        val weather = weather()

        assertEquals(start, weather.refresh(RefreshReason.START))
        assertNull(weather.refresh(RefreshReason.START), "here's forecast is fresh, so nothing new was stored for it")
    }

    @Test
    fun aSlowMorningFixAndSlowRequestStillEndAtFiveSeconds() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 6.seconds, requestTime = 4.seconds)

        weatherWith(slow).refresh(RefreshReason.MORNING)

        assertTrue(cache.rows.value.isEmpty())
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun otherRefreshesGiveTheFixTenSecondsAndTheRequestTenMore() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 8.seconds, requestTime = 9.seconds)

        weatherWith(slow).refresh(RefreshReason.START)

        assertEquals(listOf(10.seconds), slow.timeouts)
        assertEquals(setOf(leeds.key, london.key, tokyoPlace.key), cache.rows.value.keys)
        assertEquals(17_000L, currentTime)
    }

    @Test
    fun otherRefreshesFallBackWhenTheFixTakesAllTenSecondsAndThenStillRequest() = runTest {
        cities.ensureSeeded()
        cities.add(tokyo)
        cities.makeHome(tokyo.id)
        val slow = Slow(fixTime = 30.seconds, requestTime = 3.seconds)
        val weather = weatherWith(slow)

        weather.refresh(RefreshReason.WORLD)

        assertEquals(HerePlace(tokyoPlace, "Tokyo", HereSource.HOME_CITY), weather.here.first())
        assertEquals(setOf(tokyoPlace.key, london.key), cache.rows.value.keys)
        assertEquals(13_000L, currentTime)
    }

    @Test
    fun otherRefreshesGiveUpOnARequestAfterTenSeconds() = runTest {
        seedLondonAndTokyo()
        val slow = Slow(fixTime = 1.seconds, requestTime = 11.seconds)

        weatherWith(slow).refresh(RefreshReason.SETTINGS)

        assertTrue(cache.rows.value.isEmpty())
        assertEquals(11_000L, currentTime)
    }

    @Test
    fun aMorningRefreshStillSkipsAFreshCache() = runTest {
        seedLondonAndTokyo()
        val weather = weather()
        weather.refresh(RefreshReason.START)

        clock.now = start + 10.minutes
        weather.refresh(RefreshReason.MORNING)

        assertEquals(1, source.requests.size)
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
    fun aFixIsSavedWithTheNameOfItsCity() = runTest {
        seedLondonAndTokyo()
        location.fix = Coordinates(53.7997, -1.5492)

        weather().refresh(RefreshReason.START)

        assertEquals("Leeds", settings.lastLocation.first()?.name)
    }

    @Test
    fun theNameSavedWithTheLastFixSparesLoadingTheCityList() = runTest {
        var loads = 0
        val counted = CityCatalog { loads++; "" }
        val savedCities = CityRepository(FakeSavedCityDao(), counted) { phoneZone }
        savedCities.add(tokyo)
        savedCities.makeHome(tokyo.id)
        settings.saveLastLocation(leeds, start, name = "Leeds")
        val weather = WeatherRepository(settings, savedCities, counted, location, source, cache, clock, { phoneZone }, backgroundScope)

        assertEquals(HerePlace(leeds, "Leeds", HereSource.LOCATION), weather.here.first())
        assertEquals(0, loads)
    }

    @Test
    fun openingTheAppAgainSoonUsesTheLastFixRatherThanAskingAgain() = runTest {
        seedLondonAndTokyo()
        location.fix = leeds
        val weather = weather()
        weather.refresh(RefreshReason.START)

        clock.now = start + 29.minutes
        weather.refresh(RefreshReason.START)
        weather.refresh(RefreshReason.WORLD)
        assertEquals(1, location.calls)
        assertEquals(HerePlace(leeds, "Leeds", HereSource.LOCATION), weather.here.first())

        weather.refresh(RefreshReason.MANUAL)
        assertEquals(2, location.calls, "pulling World down always asks")

        clock.now = start + 1.hours
        weather.refresh(RefreshReason.START)
        assertEquals(3, location.calls, "a fix over 30 minutes old is asked for again")
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
        // The list loads on a real thread, which the refresh's timeout would not wait for in a test's virtual time.
        catalog.cityForZone(phoneZone)
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
