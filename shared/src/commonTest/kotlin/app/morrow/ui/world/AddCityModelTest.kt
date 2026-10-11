package app.morrow.ui.world

import app.morrow.places.City
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddCityModelTest {

    private val london = TimeZone.of("Europe/London")
    private val now = LocalDateTime(2026, 1, 15, 7, 40).toInstant(london)

    private val londonCity = City(2643743, "London", "England", "United Kingdom", 51.5, -0.12, "Europe/London", 8961989)
    private val tokyo = City(1850147, "Tokyo", "Tokyo", "Japan", 35.7, 139.7, "Asia/Tokyo", 9733276)
    private val singapore = City(1880252, "Singapore", null, "Singapore", 1.29, 103.85, "Asia/Singapore", 5638700)

    private fun model(query: String, found: List<City>, saved: Set<Long> = emptySet()) = addCityModel(query, found, saved, now, london)

    @Test
    fun rowsShowTheNamePlaceAndTheLocalTime() {
        val rows = model("t", listOf(tokyo, singapore)).rows

        assertEquals(listOf("Tokyo", "Singapore"), rows.map { it.name })
        assertEquals(listOf("Tokyo, Japan", "Singapore"), rows.map { it.place })
        assertEquals(listOf("16:40", "15:40"), rows.map { it.clock })
        assertEquals(listOf(tokyo, singapore), rows.map { it.city })
    }

    @Test
    fun aSavedCityIsTickedInsteadOfOfferedAgain() {
        val rows = model("lon", listOf(londonCity, tokyo), saved = setOf(2643743)).rows

        assertEquals(listOf(true, false), rows.map { it.saved })
    }

    @Test
    fun aSearchKeepsSavedCitiesSoTheyAreNotMistakenForMissing() {
        val model = model("london", listOf(londonCity), saved = setOf(2643743))

        assertEquals(1, model.rows.size)
        assertFalse(model.nothingFound)
    }

    @Test
    fun withNothingTypedThePopularCitiesHaveTheirLabelAndNoSavedOnes() {
        val model = model("", listOf(tokyo, londonCity, singapore), saved = setOf(2643743))

        assertTrue(model.showPopularLabel)
        assertEquals(listOf("Tokyo", "Singapore"), model.rows.map { it.name })
        assertTrue(model.rows.none { it.saved })
        assertFalse(model.nothingFound)
    }

    @Test
    fun blankTextCountsAsNothingTyped() {
        assertTrue(model("   ", listOf(tokyo)).showPopularLabel)
    }

    @Test
    fun aSearchHasNoPopularLabel() {
        assertFalse(model("tok", listOf(tokyo)).showPopularLabel)
    }

    @Test
    fun noMatchesForTypedTextSaysSo() {
        val model = model("zzzz", emptyList())

        assertTrue(model.nothingFound)
        assertEquals(emptyList(), model.rows)
        assertFalse(model.showPopularLabel)
    }

    @Test
    fun noPopularCitiesLeftIsNotAFailedSearch() {
        val model = model("", listOf(londonCity), saved = setOf(2643743))

        assertFalse(model.nothingFound)
        assertFalse(model.showPopularLabel)
        assertEquals(emptyList(), model.rows)
    }

    @Test
    fun theListIsCappedAtEight() {
        val many = (1L..20L).map { londonCity.copy(id = it, name = "City $it") }

        assertEquals(8, model("city", many).rows.size)
        assertEquals((1L..8L).toList(), model("", many).rows.map { it.city.id })
    }

    @Test
    fun theCapCountsOnlyCitiesNotYetSaved() {
        val many = (1L..20L).map { londonCity.copy(id = it, name = "City $it") }

        val rows = model("", many, saved = setOf(1, 2, 3)).rows

        assertEquals((4L..11L).toList(), rows.map { it.city.id })
    }
}
