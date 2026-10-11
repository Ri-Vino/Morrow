package app.morrow.places

import app.morrow.alarms.FakeClock
import app.morrow.data.FakeCitySearchCacheDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.platform.HttpException
import app.morrow.platform.HttpResponse
import app.morrow.weather.FakeHttpClient
import app.morrow.weather.GeocodedPlace
import app.morrow.weather.LonSearchJson
import app.morrow.weather.OpenMeteoGeocoder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class CitySearchTest {

    private val start = Instant.parse("2026-10-11T06:00:00Z")
    private val clock = FakeClock(start)
    private val settings = MorrowSettings(InMemoryPreferences())
    private val cache = FakeCitySearchCacheDao()
    private val catalog = CityCatalog {
        """
        2643743	London		England	United Kingdom	51.5085	-0.1257	Europe/London	8961989
        2644688	Leeds		England	United Kingdom	53.7965	-1.5479	Europe/London	455123
        """.trimIndent()
    }

    private val london = City(2643743, "London", "England", "United Kingdom", 51.5085, -0.1257, "Europe/London", 8961989)
    private val londonOntario = City(6058560, "London", "Ontario", "Canada", 42.98339, -81.23304, "America/Toronto", 346765)
    private val littleLondon = City(2644209, "Little London", "England", "United Kingdom", 51.33, -1.16, "Europe/London", 0)

    private fun search(http: FakeHttpClient) = CitySearch(catalog, OpenMeteoGeocoder(http), cache, settings.weatherEnabled, clock)

    @Test
    fun withWeatherOffNothingIsSentAndNothingCachedIsUsed() = runTest {
        val http = FakeHttpClient.ok(LonSearchJson)
        val search = search(http)
        search.online("lon")
        settings.setWeatherEnabled(false)

        assertFalse(search.mayGoOnline("lon"))
        assertEquals(OnlineSearch.Off, search.online("london"))
        assertNull(search.cached("lon"))
        assertEquals(listOf(london), search.bundled("lon", 8))
        assertEquals(1, http.urls.size)
    }

    @Test
    fun underTwoCharactersStaysOnThePhone() = runTest {
        val http = FakeHttpClient.ok(LonSearchJson)

        assertEquals(OnlineSearch.Off, search(http).online(" l "))

        assertEquals(emptyList(), http.urls)
    }

    @Test
    fun onlinePlacesBecomeCitiesWithEveryField() = runTest {
        val found = search(FakeHttpClient.ok(LonSearchJson)).online("lon")

        assertEquals(OnlineSearch.Found(listOf(london.copy(latitude = 51.50853, longitude = -0.12574), londonOntario, littleLondon)), found)
    }

    @Test
    fun eachQueryIsCachedFor30Days() = runTest {
        val http = FakeHttpClient.ok(LonSearchJson)
        val search = search(http)

        val first = search.online("Lon")
        assertEquals(first, search.online(" lon "))
        assertEquals(1, http.urls.size)
        assertTrue(cache.rows.containsKey("lon"))

        clock.now = start + 29.days
        assertEquals((first as OnlineSearch.Found).cities, search.cached("LON"))
        clock.now = start + 31.days
        assertNull(search.cached("lon"))
        search.online("lon")
        assertEquals(2, http.urls.size)
    }

    @Test
    fun oldSearchesAreForgotten() = runTest {
        val search = search(FakeHttpClient.ok(LonSearchJson))
        search.online("lon")

        clock.now = start + 31.days
        search.online("leeds")

        assertEquals(setOf("leeds"), cache.rows.keys)
    }

    @Test
    fun aFailedSearchSaysSoAndIsNotCached() = runTest {
        val offline = search(FakeHttpClient { throw HttpException("offline") })
        assertEquals(OnlineSearch.Failed, offline.online("hebden"))

        val broken = search(FakeHttpClient { HttpResponse(500, "") })
        assertEquals(OnlineSearch.Failed, broken.online("hebden"))

        assertTrue(cache.rows.isEmpty())
    }

    @Test
    fun aPlaceWithoutPopulationOrCountryStillMakesACity() {
        val city = GeocodedPlace(1, "Somewhere", region = "", country = null, latitude = 1.0, longitude = 2.0, timeZone = "UTC", population = null).toCity()

        assertEquals(City(1, "Somewhere", null, "", 1.0, 2.0, "UTC", 0), city)
    }

    @Test
    fun mergingRanksByPopulationAndDropsDuplicatesById() {
        val leeds = City(2644688, "Leeds", "England", "United Kingdom", 53.7965, -1.5479, "Europe/London", 455123)
        val onlineLondon = london.copy(latitude = 51.50853, population = 1)

        val merged = mergeCities(listOf(london, leeds), listOf(littleLondon, onlineLondon, londonOntario, londonOntario))

        assertEquals(listOf(london, leeds, londonOntario, littleLondon), merged)
    }
}
