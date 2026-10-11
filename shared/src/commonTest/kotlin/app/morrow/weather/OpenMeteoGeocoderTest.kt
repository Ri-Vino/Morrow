package app.morrow.weather

import app.morrow.platform.HttpException
import app.morrow.platform.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenMeteoGeocoderTest {

    @Test
    fun readsPlacesWithTheirGeoNamesIds() = runTest {
        val places = OpenMeteoGeocoder(FakeHttpClient.ok(SearchJson)).search("Hebden")

        assertEquals(
            listOf(
                GeocodedPlace(
                    id = 2647057,
                    name = "Hebden Bridge",
                    region = "England",
                    country = "United Kingdom",
                    latitude = 53.74233,
                    longitude = -2.01174,
                    timeZone = "Europe/London",
                    population = 4086,
                ),
                GeocodedPlace(
                    id = 2647058,
                    name = "Hebden",
                    region = null,
                    country = "United Kingdom",
                    latitude = 54.06,
                    longitude = -1.96,
                    timeZone = "Europe/London",
                    population = null,
                ),
            ),
            places,
        )
    }

    @Test
    fun asksForTheEncodedSearchText() = runTest {
        val http = FakeHttpClient.ok(NoResultsJson)
        val geocoder = OpenMeteoGeocoder(http)
        geocoder.search("  São Paulo ")
        geocoder.search("a&b=c")

        assertEquals(
            listOf(
                "https://geocoding-api.open-meteo.com/v1/search?name=S%C3%A3o%20Paulo&count=10&language=en&format=json",
                "https://geocoding-api.open-meteo.com/v1/search?name=a%26b%3Dc&count=10&language=en&format=json",
            ),
            http.urls,
        )
    }

    @Test
    fun queriesUnderTwoCharactersMakeNoRequest() = runTest {
        val http = FakeHttpClient.ok(SearchJson)
        val geocoder = OpenMeteoGeocoder(http)

        for (query in listOf("", " ", "L", "  é  ")) assertEquals(emptyList(), geocoder.search(query))
        assertEquals(emptyList(), http.urls)

        geocoder.search("Lo")
        assertEquals(1, http.urls.size)
    }

    @Test
    fun noMatchesIsAnEmptyList() = runTest {
        assertEquals(emptyList(), OpenMeteoGeocoder(FakeHttpClient.ok(NoResultsJson)).search("Xqzzy"))
    }

    @Test
    fun failuresAreFetchExceptions() = runTest {
        assertFailsWith<FetchException> { OpenMeteoGeocoder(FakeHttpClient.ok("not json")).search("London") }
        assertFailsWith<FetchException> { OpenMeteoGeocoder(FakeHttpClient { HttpResponse(503, "") }).search("London") }
        assertFailsWith<FetchException> { OpenMeteoGeocoder(FakeHttpClient { throw HttpException("offline") }).search("London") }
        val limited = assertFailsWith<FetchException> { OpenMeteoGeocoder(FakeHttpClient { HttpResponse(429, "") }).search("London") }
        assertTrue(limited.rateLimited)
    }

    private companion object {
        // From Open-Meteo's documented geocoding response; the last result has no time zone and is dropped.
        val SearchJson = """
            {
              "results": [
                {
                  "id": 2647057, "name": "Hebden Bridge", "latitude": 53.74233, "longitude": -2.01174, "elevation": 103.0,
                  "feature_code": "PPL", "country_code": "GB", "admin1_id": 6269131, "admin2_id": 3333128,
                  "timezone": "Europe/London", "population": 4086, "country_id": 2635167,
                  "country": "United Kingdom", "admin1": "England", "admin2": "Calderdale"
                },
                {
                  "id": 2647058, "name": "Hebden", "latitude": 54.06, "longitude": -1.96,
                  "feature_code": "PPL", "country_code": "GB", "timezone": "Europe/London", "country": "United Kingdom"
                },
                {
                  "id": 9999999, "name": "Hebden Rock", "latitude": 10.0, "longitude": 10.0, "feature_code": "RK"
                }
              ],
              "generationtime_ms": 0.6
            }
        """.trimIndent()

        val NoResultsJson = """{"generationtime_ms": 0.3}"""
    }
}
