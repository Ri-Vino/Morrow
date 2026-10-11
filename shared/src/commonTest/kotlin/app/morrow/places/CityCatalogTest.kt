package app.morrow.places

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CityCatalogTest {

    // As tools/make_cities.py writes it: grouped by country, not by population.
    private val tsv = """
        6058560	London		Ontario	Canada	42.9834	-81.2330	America/Toronto	422324
        6167865	Toronto		Ontario	Canada	43.7064	-79.3986	America/Toronto	2794356
        3413829	Reykjavík	Reykjavik	Capital Region	Iceland	64.1355	-21.8954	Atlantic/Reykjavik	118918
        2643743	London		England	United Kingdom	51.5085	-0.1257	Europe/London	8961989
        2655603	Birmingham		England	United Kingdom	52.4814	-1.8998	Europe/London	984333
        5128581	New York City		New York	United States	40.7143	-74.0060	America/New_York	8804190
        5128638	New Rochelle		New York	United States	40.9115	-73.7824	America/New_York	79726
        4167147	Orlando		Florida	United States	28.5383	-81.3792	America/New_York	307573
        5106292	West New York		New Jersey	United States	40.7857	-74.0143	America/New_York	52912
        1880252	Singapore			Singapore	1.2897	103.8501	Asia/Singapore	5638700
        2193733	Auckland		Auckland	New Zealand	-36.8485	174.7633	Pacific/Auckland	417910
        4032243	Nukuʻalofa	Nuku`alofa	Tongatapu	Tonga	-21.1394	-175.2018	Pacific/Tongatapu	22400
        1275339	Mumbai		Maharashtra	India	19.0728	72.8826	Asia/Kolkata	12691836
        2988507	Paris		Île-de-France	France	48.8534	2.3488	Europe/Paris	2138551
        2988506	Saint-Denis		Île-de-France	France	48.9362	2.3574	Europe/Paris	112309
    """.trimIndent()

    private val catalog = CityCatalog { tsv }

    @Test
    fun parsesEveryColumn() {
        val cities = parseCities(tsv).associateBy { it.city.id }
        assertEquals(15, cities.size)
        val reykjavik = cities.getValue(3413829)
        assertEquals(
            City(3413829, "Reykjavík", "Capital Region", "Iceland", 64.1355, -21.8954, "Atlantic/Reykjavik", 118918),
            reykjavik.city,
        )
        assertEquals("Reykjavik", reykjavik.asciiName)
        // An empty ASCII name means it's the same as the name, and an empty region means there's none.
        val singapore = cities.getValue(1880252)
        assertNull(singapore.asciiName)
        assertNull(singapore.city.region)
        assertEquals("Île-de-France", cities.getValue(2988507).city.region)
    }

    @Test
    fun skipsLinesThatDontFit() {
        val cities = parseCities("id\tname\n\n1\tA\t\t\tX\t1.0\t2.0\tEurope/Paris\t5\n2\tB\t\t\tX\tnorth\t2.0\tEurope/Paris\t5\n")
        assertEquals(listOf(1L), cities.map { it.city.id })
    }

    @Test
    fun searchFindsPrefixesOfTheNameMostPopulousFirst() = runTest {
        assertEquals(listOf("London, United Kingdom", "London, Canada"), catalog.search("lond").map(::label))
        assertEquals(listOf("New York City", "New Rochelle", "West New York"), catalog.search("NEW").map { it.name })
    }

    @Test
    fun searchFindsPrefixesOfLaterWords() = runTest {
        assertEquals(listOf("New York City", "West New York"), catalog.search("york").map { it.name })
        assertEquals(listOf("Saint-Denis"), catalog.search("denis").map { it.name })
        // Not from the middle of a word.
        assertEquals(emptyList(), catalog.search("ondon"))
    }

    @Test
    fun searchIgnoresAccentsThroughTheAsciiName() = runTest {
        assertEquals(listOf("Reykjavík"), catalog.search("reykjavik").map { it.name })
        assertEquals(listOf("Reykjavík"), catalog.search("Reykjavík").map { it.name })
        assertEquals(listOf("Nukuʻalofa"), catalog.search("nuku`a").map { it.name })
    }

    @Test
    fun searchStopsAtTheLimit() = runTest {
        assertEquals(listOf("New York City"), catalog.search("new", limit = 1).map { it.name })
        assertEquals(3, catalog.search("new").size)
        assertEquals(emptyList(), catalog.search("new", limit = 0))
    }

    @Test
    fun blankSearchFindsNothing() = runTest {
        assertEquals(emptyList(), catalog.search(""))
        assertEquals(emptyList(), catalog.search("   "))
    }

    @Test
    fun nearestNamesTheClosestCity() = runTest {
        assertEquals("London, United Kingdom", catalog.nearest(51.45, -0.3)?.let(::label))
        assertEquals("Saint-Denis", catalog.nearest(48.93, 2.36)?.name)
        assertEquals("New York City", catalog.nearest(40.72, -74.0)?.name)
        // Across the date line, Nukuʻalofa (-175.2) is nearer than Auckland (174.8).
        assertEquals("Nukuʻalofa", catalog.nearest(-21.0, 179.9)?.name)
    }

    @Test
    fun nearestOfNoCitiesIsNull() = runTest {
        assertNull(CityCatalog { "" }.nearest(0.0, 0.0))
    }

    @Test
    fun cityForZoneIsTheMostPopulousThere() = runTest {
        assertEquals("New York City", catalog.cityForZone("America/New_York")?.name)
        assertEquals("Toronto", catalog.cityForZone("America/Toronto")?.name)
        assertEquals("London, United Kingdom", catalog.cityForZone("Europe/London")?.let(::label))
        assertNull(catalog.cityForZone("Europe/Lisbon"))
        assertNull(catalog.cityForZone("UTC"))
    }

    @Test
    fun cityForZoneKnowsOldZoneNames() = runTest {
        assertEquals("Mumbai", catalog.cityForZone("Asia/Calcutta")?.name)
        // Montreal's old zone follows Toronto's clock.
        assertEquals("Toronto", catalog.cityForZone("America/Montreal")?.name)
    }

    @Test
    fun byIdFindsACity() = runTest {
        assertEquals("Birmingham", catalog.byId(2655603)?.name)
        assertNull(catalog.byId(42))
    }

    @Test
    fun mostPopulousListsTheBiggestCitiesFirstAndSkipsTheExcepted() = runTest {
        assertEquals(listOf(1275339L, 2643743L, 5128581L), catalog.mostPopulous(3).map { it.id })
        assertEquals(listOf(2643743L, 5128581L), catalog.mostPopulous(2, except = setOf(1275339L)).map { it.id })
        assertEquals(emptyList(), catalog.mostPopulous(0))
        assertEquals(15, catalog.mostPopulous(100).size)
    }

    @Test
    fun loadsOnceForManyCalls() = runTest {
        var loads = 0
        val counted = CityCatalog { loads++; tsv }
        assertEquals(0, loads)
        counted.search("lon")
        counted.nearest(0.0, 0.0)
        counted.cityForZone("Europe/London")
        assertEquals(1, loads)
    }

    @Test
    fun wordStartsCountAfterAnyPunctuation() {
        assertTrue(startsAnyWord("stratford-upon-avon", "avon"))
        assertTrue(startsAnyWord("st. john's", "john"))
        assertTrue(startsAnyWord("new york city", "york c"))
        assertEquals(false, startsAnyWord("york", "yorkshire"))
    }

    private fun label(city: City) = if (city.name == "London") "London, ${city.country}" else city.name
}
