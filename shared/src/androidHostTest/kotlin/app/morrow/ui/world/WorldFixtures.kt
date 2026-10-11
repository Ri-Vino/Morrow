package app.morrow.ui.world

import app.morrow.alarms.FakeClock
import app.morrow.data.FakeCitySearchCacheDao
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.places.City
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.places.CitySearch
import app.morrow.platform.HttpResponse
import app.morrow.weather.FakeHttpClient
import app.morrow.weather.OpenMeteoGeocoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** A small world for the World and Add a city tests: the phone is in London at 07:40 on a January morning. */
internal class WorldFixtures {
    val phoneZone: TimeZone = TimeZone.of("Europe/London")
    val now: Instant = LocalDateTime(2026, 1, 15, 7, 40).toInstant(phoneZone)
    val ticks: Flow<Instant> = flowOf(now)

    val catalog = CityCatalog {
        """
        2643743	London		England	United Kingdom	51.5085	-0.1257	Europe/London	8961989
        2655603	Birmingham		England	United Kingdom	52.4814	-1.8998	Europe/London	984333
        5128581	New York City		New York	United States	40.7143	-74.0060	America/New_York	8804190
        1850147	Tokyo		Tokyo	Japan	35.6895	139.6917	Asia/Tokyo	9733276
        3413829	Reykjavík	Reykjavik	Capital Region	Iceland	64.1355	-21.8954	Atlantic/Reykjavik	118918
        """.trimIndent()
    }
    val cities = CityRepository(FakeSavedCityDao(), catalog) { "Europe/London" }

    /** Weather is on by default, as in the app; online search answers with [geocoderAnswer]. */
    val settings = MorrowSettings(InMemoryPreferences())
    var geocoderAnswer: (String) -> HttpResponse = { HttpResponse(200, """{"generationtime_ms": 0.3}""") }
    val http = FakeHttpClient { geocoderAnswer(it) }
    val searchCache = FakeCitySearchCacheDao()
    val search = CitySearch(catalog, OpenMeteoGeocoder(http), searchCache, settings.weatherEnabled, FakeClock(now))

    val london = City(2643743, "London", "England", "United Kingdom", 51.5085, -0.1257, "Europe/London", 8961989)
    val newYork = City(5128581, "New York City", "New York", "United States", 40.7143, -74.0060, "America/New_York", 8804190)
    val tokyo = City(1850147, "Tokyo", "Tokyo", "Japan", 35.6895, 139.6917, "Asia/Tokyo", 9733276)
}
