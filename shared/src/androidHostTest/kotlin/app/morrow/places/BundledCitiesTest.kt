package app.morrow.places

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/** The list the app ships, as tools/make_cities.py wrote it. */
class BundledCitiesTest {

    private val tsv = File("src/commonMain/composeResources/files/cities.tsv").readText()
    private val catalog = CityCatalog { tsv }

    @Test
    fun parsesQuickly() {
        val (table, time) = measureTimedValue { CityTable.of(parseCities(tsv)) }
        println("Parsed ${table.size} cities in $time")
        assertTrue(table.size > 20_000, "${table.size} cities")
    }

    @Test
    fun findsWellKnownCities() = runTest {
        assertEquals(london, catalog.search("lond").first().id)
        assertEquals("Reykjavík", catalog.search("reykjavik").first().name)
        assertEquals("New York City", catalog.search("york").first().name)
        assertEquals("Tokyo", catalog.search("tok").first().name)
    }

    @Test
    fun namesPlacesAndZones() = runTest {
        assertEquals(london, catalog.cityForZone("Europe/London")?.id)
        assertEquals(london, catalog.nearest(51.5, -0.12)?.id)
        assertEquals("Mumbai", catalog.cityForZone("Asia/Calcutta")?.name)
    }

    @Test
    fun everyCityHasAZoneAndCountry() = runTest {
        val cities = parseCities(tsv)
        assertEquals(tsv.lines().count { it.isNotBlank() }, cities.size, "every line parses")
        assertTrue(cities.all { it.city.timeZone.contains('/') && it.city.country.isNotBlank() })
        assertEquals(cities.size, cities.map { it.city.id }.toSet().size, "ids are unique")
    }

    private val london = 2643743L
}
