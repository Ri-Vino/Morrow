package app.morrow.ui.world

import app.morrow.places.City
import app.morrow.places.SavedCity
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldModelTest {

    private val london = TimeZone.of("Europe/London")
    private val now = LocalDateTime(2026, 1, 15, 7, 40).toInstant(london)

    private val londonCity = City(2643743, "London", "England", "United Kingdom", 51.5, -0.12, "Europe/London", 8961989)
    private val newYork = City(5128581, "New York City", "New York", "United States", 40.7, -74.0, "America/New_York", 8804190)
    private val tokyo = City(1850147, "Tokyo", "Tokyo", "Japan", 35.7, 139.7, "Asia/Tokyo", 9733276)
    private val singapore = City(1880252, "Singapore", null, "Singapore", 1.29, 103.85, "Asia/Singapore", 5638700)

    private val saved = listOf(SavedCity(londonCity, isHome = true), SavedCity(tokyo, false), SavedCity(newYork, false))

    @Test
    fun theHeaderNamesThePhonesTimeAndTheHomeCity() {
        assertEquals("Your time 07:40 · London", worldModel(saved, now, london).header)
    }

    @Test
    fun theHeaderFollowsTheHomeCityWhereverItIsInTheList() {
        val model = worldModel(listOf(SavedCity(tokyo, false), SavedCity(newYork, isHome = true)), now, london)
        assertEquals("Your time 07:40 · New York City", model.header)
    }

    @Test
    fun theHeaderIsJustTheTimeWithNoHomeCity() {
        assertEquals("Your time 07:40", worldModel(emptyList(), now, london).header)
        assertEquals("Your time 07:40", worldModel(listOf(SavedCity(tokyo, false)), now, london).header)
    }

    @Test
    fun theHeaderUsesThePhonesZoneNotTheHomeCitys() {
        val phone = TimeZone.of("Asia/Tokyo")
        assertEquals("Your time 16:40 · London", worldModel(saved, now, phone).header)
    }

    @Test
    fun cardsKeepTheSavedOrderWithEachCitysOwnTime() {
        val cards = worldModel(saved, now, london).cards

        assertEquals(listOf("London", "Tokyo", "New York City"), cards.map { it.name })
        assertEquals(listOf("07:40", "16:40", "02:40"), cards.map { it.clock })
        assertEquals(listOf(7 * 60 + 40, 16 * 60 + 40, 2 * 60 + 40), cards.map { it.minute })
        assertEquals(listOf("Today · your time", "Today · +9 h", "Today · −5 h"), cards.map { it.relative })
        assertEquals(listOf(true, false, false), cards.map { it.isHome })
        assertEquals(listOf(2643743L, 1850147L, 5128581L), cards.map { it.id })
    }

    @Test
    fun aCardPastMidnightSaysTomorrow() {
        val late = LocalDateTime(2026, 1, 15, 21, 0).toInstant(london)
        val card = worldModel(saved, late, london).cards[1]
        assertEquals("06:00", card.clock)
        assertEquals("Tomorrow · +9 h", card.relative)
    }

    @Test
    fun theFirstCardCantMoveUpAndTheLastCantMoveDown() {
        val cards = worldModel(saved, now, london).cards
        assertEquals(listOf(false, true, true), cards.map { it.canMoveUp })
        assertEquals(listOf(true, true, false), cards.map { it.canMoveDown })

        val only = worldModel(listOf(SavedCity(londonCity, true)), now, london).cards.single()
        assertFalse(only.canMoveUp)
        assertFalse(only.canMoveDown)
    }

    @Test
    fun noCitiesMeansNoCards() {
        assertEquals(emptyList(), worldModel(emptyList(), now, london).cards)
    }

    @Test
    fun anUnknownZoneFallsBackToThePhonesClock() {
        val odd = londonCity.copy(id = 1, timeZone = "Nowhere/Land")
        val card = worldModel(listOf(SavedCity(odd, false)), now, london).cards.single()
        assertEquals("07:40", card.clock)
    }

    @Test
    fun theSheetNamesTheCityItsPlaceAndItsTime() {
        val sheet = citySheet(SavedCity(newYork, false), now, london)

        assertEquals("New York City", sheet.name)
        assertEquals("New York, United States", sheet.place)
        assertEquals("02:40", sheet.clock)
        assertEquals("Today · −5 h", sheet.relative)
        assertFalse(sheet.isHome)
        assertTrue(citySheet(SavedCity(londonCity, true), now, london).isHome)
    }

    @Test
    fun aNewAlarmFromTheSheetKeepsTheCitysClock() {
        assertEquals("New alarm in New York City time", citySheet(SavedCity(newYork, false), now, london).newAlarm)
        assertEquals("New alarm", citySheet(SavedCity(londonCity, true), now, london).newAlarm)
    }

    @Test
    fun aPlaceWithoutARegionIsJustTheCountry() {
        assertEquals("Singapore", placeName(singapore))
        assertEquals("England, United Kingdom", placeName(londonCity))
    }
}
