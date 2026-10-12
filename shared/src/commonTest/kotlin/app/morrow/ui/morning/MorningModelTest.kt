package app.morrow.ui.morning

import app.morrow.weather.Coordinates
import app.morrow.weather.CurrentWeather
import app.morrow.weather.DayWeather
import app.morrow.weather.Forecast
import app.morrow.weather.HerePlace
import app.morrow.weather.HereSource
import app.morrow.weather.HourWeather
import app.morrow.weather.PlaceWeather
import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.WeatherState
import app.morrow.weather.weatherAge
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MorningModelTest {

    private val zone = TimeZone.of("Europe/London")
    private val woke = LocalDateTime(2026, 10, 8, 6, 42).toInstant(zone)

    @Test
    fun summarisesABelievableNight() {
        val model = morningModel(woke, wentToBed = woke - 7.hours - 10.minutes, zone)
        assertEquals("Good morning.", model.hello)
        assertEquals("You were up at 06:42, after about 7h 10m of sleep.", model.summary)
    }

    @Test
    fun leavesOutSleepWhenTheBedtimeGuessIsUnlikely() {
        val nap = morningModel(woke, wentToBed = woke - 30.minutes, zone)
        val stale = morningModel(woke, wentToBed = woke - 3.days, zone)
        val unknown = morningModel(woke, wentToBed = null, zone)
        for (model in listOf(nap, stale, unknown)) {
            assertEquals("You were up at 06:42. Nicely done.", model.summary)
        }
    }

    @Test
    fun greetsByTheTimeOfWaking() {
        val afternoon = LocalDateTime(2026, 10, 8, 14, 0).toInstant(zone)
        assertEquals("Good afternoon.", morningModel(afternoon, null, zone).hello)
    }

    // The weather card

    private val place = Coordinates(51.51, -0.13)
    private val now = LocalDateTime(2026, 10, 12, 7, 15).toInstant(zone)
    private val seven = LocalDateTime(2026, 10, 12, 7, 0).toInstant(zone)

    private fun state(forecast: Forecast?, at: Instant = now, name: String? = "London", enabled: Boolean = true) = WeatherState(
        enabled = enabled,
        here = HerePlace(place, name, HereSource.LOCATION),
        hereWeather = PlaceWeather(place, forecast, weatherAge(forecast?.fetchedAt, at)),
        cities = emptyMap(),
    )

    /** 12° and raining until 11:00, with a high of 14° and a low of 8°. */
    private fun rainy(fetchedAt: Instant = now, temperature: Double = 12.0, high: Double = 14.0, low: Double = 8.0) = Forecast(
        place = place,
        timeZone = "Europe/London",
        fetchedAt = fetchedAt,
        now = CurrentWeather(temperature, Sky.RAIN, isDay = true),
        today = DayWeather(high, low, rainChance = 80),
        hours = List(8) { HourWeather(seven + it.hours, temperature, if (it < 4) Sky.RAIN else Sky.CLOUDY, rainChance = if (it < 4) 80 else 10) },
    )

    private fun card(forecast: Forecast?, at: Instant = now, units: Units = Units.CELSIUS, name: String? = "London", cardZone: TimeZone = zone) =
        morningWeather(state(forecast, at, name), units, at, cardZone)

    @Test
    fun theCardSaysHowItIsOutAndWhatToDoAboutIt() {
        val card = assertNotNull(card(rainy()))

        assertEquals("12° and rainy in London", card.headline)
        assertEquals("High 14° · Low 8° · Rain until about 11:00", card.detail)
        assertEquals("Take an umbrella. It dries up this morning.", card.tip)
        assertEquals(Sky.RAIN, card.sky)
    }

    @Test
    fun pipReadsTheGreetingTheWeatherAndTheTip() {
        val card = assertNotNull(card(rainy()))

        assertEquals(
            "Good morning. It's 12 degrees and rainy in London, with a high of 14. Take an umbrella. It dries up this morning.",
            card.speech("Good morning."),
        )
    }

    @Test
    fun fahrenheitIsConvertedAndRounded() {
        val card = assertNotNull(card(rainy(), units = Units.FAHRENHEIT))

        assertEquals("54° and rainy in London", card.headline)
        assertEquals("High 57° · Low 46° · Rain until about 11:00", card.detail)
        assertEquals("Good morning. It's 54 degrees and rainy in London, with a high of 57. Take an umbrella. It dries up this morning.", card.speech("Good morning."))
    }

    @Test
    fun belowZeroIsSpokenAsMinus() {
        val card = assertNotNull(card(rainy(temperature = -2.6, high = 0.4, low = -5.0)))

        assertEquals("-3° and rainy in London", card.headline)
        assertEquals("High 0° · Low -5° · Rain until about 11:00", card.detail)
        assertTrue(card.speech("Hello.").startsWith("Hello. It's minus 3 degrees and rainy in London, with a high of 0."))
    }

    @Test
    fun withoutAPlaceNameItIsJustHowItIs() {
        val card = assertNotNull(card(rainy(), name = null))

        assertEquals("12° and rainy", card.headline)
        assertTrue(card.speech("Hello.").startsWith("Hello. It's 12 degrees and rainy, with a high of 14."))
    }

    @Test
    fun aDayWithNothingToSayHasNoTip() {
        val grey = rainy().copy(now = CurrentWeather(12.0, Sky.CLOUDY, isDay = true), hours = List(8) { HourWeather(seven + it.hours, 12.0, Sky.CLOUDY, 10) })
        val card = assertNotNull(card(grey))

        assertNull(card.tip)
        assertEquals("12° and cloudy in London", card.headline)
        assertEquals("High 14° · Low 8° · Dry under grey skies", card.detail)
        assertEquals("Good morning. It's 12 degrees and cloudy in London, with a high of 14.", card.speech("Good morning."))
    }

    @Test
    fun aFreshForecastWithNoHoursHasJustHighAndLow() {
        val card = assertNotNull(card(rainy().copy(hours = emptyList())))

        assertEquals("12° and rainy in London", card.headline)
        assertEquals("High 14° · Low 8°", card.detail)
    }

    @Test
    fun aForecastFromTodayWhoseHoursHaveRunOutIsHiddenNotShownAsHowItIsNow() {
        val afterMidnight = LocalDateTime(2026, 10, 12, 0, 30).toInstant(zone)
        val forecast = rainy(fetchedAt = afterMidnight)

        assertNotNull(card(forecast, at = seven + 7.hours + 30.minutes), "the last hour is 14:00, so 14:30 is still covered")
        assertNull(card(forecast, at = seven + 8.hours + 1.minutes))
        assertNull(card(forecast, at = seven + 9.hours))
    }

    @Test
    fun aClearSkyAfterDarkIsNight() {
        val clear = rainy().copy(now = CurrentWeather(4.0, Sky.CLEAR, isDay = false))

        assertTrue(assertNotNull(card(clear)).night)
        assertFalse(assertNotNull(card(clear.copy(now = clear.now.copy(isDay = true)))).night)
    }

    @Test
    fun withWeatherOffThereIsNoCard() {
        assertNull(morningWeather(WeatherState.Off, Units.CELSIUS, now, zone))
        assertNull(morningWeather(state(rainy(), enabled = false), Units.CELSIUS, now, zone))
    }

    @Test
    fun withNoForecastThereIsNoCard() {
        assertNull(card(null))
        assertNull(morningWeather(state(rainy()).copy(hereWeather = null), Units.CELSIUS, now, zone))
        assertNull(morningWeather(WeatherState(true, null, null, emptyMap()), Units.CELSIUS, now, zone))
    }

    @Test
    fun aForecastOverADayOldIsHidden() {
        assertNull(card(rainy(fetchedAt = now - 25.hours)))
        assertNull(card(rainy(fetchedAt = now - 3.days)))
    }

    @Test
    fun yesterdaysForecastIsHiddenHoweverRecentlyItWasFetched() {
        val lateLastNight = LocalDateTime(2026, 10, 11, 23, 50).toInstant(zone)
        val justAfterMidnight = LocalDateTime(2026, 10, 12, 0, 20).toInstant(zone)

        assertNull(card(rainy(fetchedAt = lateLastNight), at = justAfterMidnight))
        assertNotNull(card(rainy(fetchedAt = justAfterMidnight - 10.minutes), at = justAfterMidnight), "ten minutes before is already today")
    }

    @Test
    fun todaysOlderForecastIsStillShown() {
        val afterMidnight = LocalDateTime(2026, 10, 12, 0, 30).toInstant(zone)

        assertNotNull(card(rainy(fetchedAt = afterMidnight)))
    }

    @Test
    fun todayIsTodayOnThePhonesClock() {
        val fetched = LocalDateTime(2026, 10, 12, 0, 30).toInstant(zone)
        val phoneInUtc = TimeZone.UTC

        assertNotNull(card(rainy(fetchedAt = fetched), cardZone = zone))
        assertNull(card(rainy(fetchedAt = fetched), cardZone = phoneInUtc), "00:30 in London is still yesterday in UTC")
    }

    @Test
    fun aForecastFromTomorrowIsHidden() {
        assertNull(card(rainy(fetchedAt = now + 1.days)))
    }
}
