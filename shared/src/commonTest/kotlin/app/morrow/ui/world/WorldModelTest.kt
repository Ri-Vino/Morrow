package app.morrow.ui.world

import app.morrow.places.City
import app.morrow.places.SavedCity
import app.morrow.weather.CurrentWeather
import app.morrow.weather.DayWeather
import app.morrow.weather.Forecast
import app.morrow.weather.HourWeather
import app.morrow.weather.PlaceWeather
import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.WeatherState
import app.morrow.weather.coordinates
import app.morrow.weather.weatherAge
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    // Weather: London rains at 12°, Tokyo is partly cloudy, New York is clear in the small hours.

    private fun forecast(city: City, fetched: Instant, temperature: Double, sky: Sky, high: Double, low: Double, rain: Int? = 80) = Forecast(
        place = city.coordinates,
        timeZone = city.timeZone,
        fetchedAt = fetched,
        now = CurrentWeather(temperature, sky, isDay = true),
        today = DayWeather(high, low, rain),
        hours = List(12) { i ->
            val start = LocalDateTime(2026, 1, 15, 7, 0).toInstant(london) + i.hours
            HourWeather(start, temperature + i, if (i < 4) sky else Sky.PARTLY_CLOUDY, 40)
        },
    )

    private fun weather(fetched: Instant = now - 5.minutes, vararg skipped: City) = WeatherState(
        enabled = true,
        here = null,
        hereWeather = null,
        cities = listOf(
            londonCity to forecast(londonCity, fetched, 12.0, Sky.RAIN, 14.0, 8.0),
            tokyo to forecast(tokyo, fetched, 21.0, Sky.PARTLY_CLOUDY, 23.0, 16.0),
            newYork to forecast(newYork, fetched, 11.0, Sky.CLEAR, 17.0, 9.0),
        ).filter { it.first !in skipped }.associate { (city, forecast) ->
            city.id to PlaceWeather(city.coordinates, forecast, weatherAge(forecast.fetchedAt, fetched))
        },
    )

    @Test
    fun cardsShowTheTemperatureTheConditionAndTheHighAndLow() {
        val cards = worldModel(saved, now, london, weather()).cards

        assertEquals(listOf("12° Rain", "21° Partly cloudy", "11° Clear"), cards.map { it.weather?.conditions })
        assertEquals(listOf("H 14°  L 8°", "H 23°  L 16°", "H 17°  L 9°"), cards.map { it.weather?.highLow })
        assertEquals(listOf(Sky.RAIN, Sky.PARTLY_CLOUDY, Sky.CLEAR), cards.map { it.weather?.sky })
        assertEquals("12° and rain", cards[0].weather?.spoken)
        assertEquals("21° and partly cloudy", cards[1].weather?.spoken)
    }

    @Test
    fun aClearNightGetsTheMoonForItsIcon() {
        val cards = worldModel(saved, now, london, weather()).cards
        // London at 07:40 is past sunrise in the scene; New York at 02:40 is the middle of the night.
        assertEquals(listOf(false, false, true), cards.map { it.weather?.night })
    }

    @Test
    fun pastTheCitysMidnightItsCardHasTheNewDaysHighAndLow() {
        val evening = LocalDateTime(2026, 1, 15, 22, 0).toInstant(london)
        val small = LocalDateTime(2026, 1, 16, 1, 0).toInstant(london)
        val fetched = forecast(londonCity, evening, 12.0, Sky.RAIN, 14.0, 8.0)
            .copy(tomorrow = DayWeather(10.0, 3.0, 20), hours = List(12) { i -> HourWeather(evening + i.hours, 9.0, Sky.CLOUDY, 20) })
        val state = WeatherState(true, null, null, mapOf(londonCity.id to PlaceWeather(londonCity.coordinates, fetched, weatherAge(evening, small))))

        val card = worldModel(saved, small, london, state).cards.first()
        val sheet = citySheet(saved.first(), small, london, state)

        assertEquals("H 10°  L 3°", card.weather?.highLow)
        assertEquals("High 10° · Low 3° · 20% chance of rain", sheet.day)
    }

    @Test
    fun cardsCanShowFahrenheit() {
        val card = worldModel(saved, now, london, weather(), Units.FAHRENHEIT).cards.first()
        assertEquals("54° Rain", card.weather?.conditions)
        assertEquals("H 57°  L 46°", card.weather?.highLow)
    }

    @Test
    fun withWeatherOffCardsShowTheTimeOfDayOnly() {
        val model = worldModel(saved, now, london, WeatherState.Off)

        assertTrue(model.cards.all { it.weather == null })
        assertEquals(listOf("07:40", "16:40", "02:40"), model.cards.map { it.clock })
        assertEquals("Weather is off, so cards show the time of day only.", model.footer)
    }

    @Test
    fun weatherOverADayOldIsNotShown() {
        val fetched = now - 24.hours - 1.minutes
        val model = worldModel(saved, now, london, weather(fetched))

        assertTrue(model.cards.all { it.weather == null })
        assertEquals("No recent weather, so cards show the time of day only.", model.footer)
    }

    @Test
    fun aCityWithNoForecastYetShowsItsTimeOnly() {
        val cards = worldModel(saved, now, london, weather(now - 5.minutes, tokyo)).cards
        assertEquals(listOf(true, false, true), cards.map { it.weather != null })
    }

    @Test
    fun theFooterSaysHowOldTheWeatherIs() {
        assertEquals("Weather by Open-Meteo.com · updated 5 min ago", worldModel(saved, now, london, weather(now - 5.minutes)).footer)
        assertEquals("Weather by Open-Meteo.com · updated just now", worldModel(saved, now, london, weather(now - 20.seconds)).footer)
        assertEquals("Weather by Open-Meteo.com · updated 180 min ago", worldModel(saved, now, london, weather(now - 3.hours)).footer)
        assertEquals("Weather by Open-Meteo.com · from 4 hours ago", worldModel(saved, now, london, weather(now - 4.hours - 10.minutes)).footer)
        assertEquals("Weather by Open-Meteo.com · from 24 hours ago", worldModel(saved, now, london, weather(now - 24.hours)).footer)
    }

    @Test
    fun theFooterGoesByTheOldestWeatherOnTheCards() {
        val state = weather(now - 4.hours)
        val fresher = state.copy(cities = state.cities + (tokyo.id to state.cities.getValue(tokyo.id).copy(forecast = forecast(tokyo, now - 2.minutes, 21.0, Sky.CLEAR, 23.0, 16.0))))
        assertEquals("Weather by Open-Meteo.com · from 4 hours ago", worldModel(saved, now, london, fresher).footer)
    }

    @Test
    fun beforeTheWeatherLoadsThereIsNoFooter() {
        assertNull(worldModel(saved, now, london).footer)
        assertNull(worldModel(saved, now, london, null).footer)
    }

    @Test
    fun oldWeatherShowsTheForecastForTheHourItIsNow() {
        // Fetched at 07:35 with London at 12° and raining; by 10:40 the forecast says 15°.
        val later = now + 3.hours
        val card = worldModel(saved, later, london, weather(now - 5.minutes)).cards.first()
        assertEquals("15° Rain", card.weather?.conditions)
    }

    @Test
    fun theSheetAddsTheNextSixHoursAndTheDay() {
        val sheet = citySheet(SavedCity(londonCity, true), now, london, weather())

        assertEquals("12° Rain", sheet.conditions)
        assertEquals(listOf("08", "09", "10", "11", "12", "13"), sheet.hours.map { it.time })
        assertEquals(listOf("13°", "14°", "15°", "16°", "17°", "18°"), sheet.hours.map { it.temperature })
        assertEquals(listOf(Sky.RAIN, Sky.RAIN, Sky.RAIN, Sky.PARTLY_CLOUDY, Sky.PARTLY_CLOUDY, Sky.PARTLY_CLOUDY), sheet.hours.map { it.sky })
        assertEquals("08:00, 13°, rain", sheet.hours.first().spoken)
        assertEquals("High 14° · Low 8° · 80% chance of rain", sheet.day)
    }

    @Test
    fun theSheetsHoursAreOnTheCitysClock() {
        val sheet = citySheet(SavedCity(tokyo, false), now, london, weather())
        assertEquals(listOf("17", "18", "19", "20", "21", "22"), sheet.hours.map { it.time })
        assertEquals(listOf(false, false, true, true, true, true), sheet.hours.map { it.night })
    }

    @Test
    fun theDayLineLeavesOutAnUnknownChanceOfRain() {
        val state = weather()
        val unknown = state.copy(cities = state.cities + (londonCity.id to state.cities.getValue(londonCity.id).copy(forecast = forecast(londonCity, now - 5.minutes, 12.0, Sky.RAIN, 14.0, 8.0, rain = null))))
        assertEquals("High 14° · Low 8°", citySheet(SavedCity(londonCity, true), now, london, unknown).day)
    }

    @Test
    fun withWeatherOffTheSheetHasNoWeather() {
        for (state in listOf(null, WeatherState.Off, weather(now - 25.hours))) {
            val sheet = citySheet(SavedCity(londonCity, true), now, london, state)
            assertNull(sheet.conditions)
            assertEquals(emptyList(), sheet.hours)
            assertNull(sheet.day)
        }
    }
}
