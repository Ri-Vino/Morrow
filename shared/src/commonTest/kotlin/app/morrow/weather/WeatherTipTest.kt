package app.morrow.weather

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WeatherTipTest {

    private val london = TimeZone.of("Europe/London")

    /** 07:15 in London, a quarter of the way through the first hour of every forecast below. */
    private val now = LocalDateTime(2026, 10, 12, 7, 15).toInstant(london)
    private val seven = LocalDateTime(2026, 10, 12, 7, 0).toInstant(london)

    /** One hour per sky from 07:00, each with [rainChance] and [temperature]. */
    private fun forecast(
        vararg skies: Sky,
        temperature: Double = 12.0,
        high: Double = 16.0,
        rainChance: Int? = 10,
        current: Sky = skies.firstOrNull() ?: Sky.CLEAR,
        fetchedAt: Instant = now,
        hourTemperatures: List<Double>? = null,
    ) = Forecast(
        place = Coordinates(51.51, -0.13),
        timeZone = "Europe/London",
        fetchedAt = fetchedAt,
        now = CurrentWeather(temperature, current, isDay = true),
        today = DayWeather(high = high, low = 4.0, rainChance = rainChance),
        hours = skies.mapIndexed { i, sky -> HourWeather(seven + i.hours, hourTemperatures?.get(i) ?: temperature, sky, rainChance) },
    )

    private fun tip(forecast: Forecast, at: Instant = now, zone: TimeZone = london) = weatherTip(forecast, at, zone)

    private fun line(forecast: Forecast, at: Instant = now, zone: TimeZone = london) = dayLine(forecast, at, zone)

    private fun skies(vararg runs: Pair<Sky, Int>): Array<Sky> = runs.flatMap { (sky, n) -> List(n) { sky } }.toTypedArray()

    // The tip

    @Test
    fun aStormNowThatEndsSaysWhenItCalmsDown() {
        val storm = forecast(*skies(Sky.STORM to 8, Sky.CLOUDY to 4))
        assertEquals("Stay in if you can. It calms down this afternoon.", tip(storm))
    }

    @Test
    fun aStormNowThatLastsAllDayDoesNotPromiseAnEnd() {
        assertEquals("Stay in if you can. It's stormy for a while yet.", tip(forecast(*skies(Sky.STORM to 12))))
    }

    @Test
    fun aStormLaterWarnsOfWhen() {
        val storm = forecast(*skies(Sky.CLEAR to 9, Sky.STORM to 2, Sky.CLOUDY to 1))
        assertEquals("Storms are forecast this afternoon. Try not to get caught out.", tip(storm))
    }

    @Test
    fun aStormBeatsSnowAndRain() {
        assertEquals(
            "Storms are forecast this morning. Try not to get caught out.",
            tip(forecast(Sky.RAIN, Sky.SNOW, Sky.STORM, Sky.CLOUDY)),
        )
        assertEquals(
            "Storms are forecast after lunch. Try not to get caught out.",
            tip(forecast(*skies(Sky.SNOW to 3, Sky.RAIN to 2, Sky.STORM to 1, Sky.CLOUDY to 1))),
        )
    }

    @Test
    fun snowBeatsRainAndTheRest() {
        val snow = forecast(Sky.RAIN, Sky.SNOW, Sky.CLEAR, high = 35.0)
        assertEquals("Wrap up warm, and mind the pavements.", tip(snow))
    }

    @Test
    fun rainThatFreezesWarnsOfIce() {
        assertEquals("It could be icy underfoot. Mind the pavements.", tip(forecast(Sky.RAIN, Sky.CLOUDY, temperature = -1.0)))
        assertEquals("It could be icy underfoot. Mind the pavements.", tip(forecast(Sky.CLOUDY, Sky.DRIZZLE, temperature = 0.0)))
    }

    @Test
    fun rainAboveFreezingIsJustRain() {
        assertEquals("Take an umbrella. It dries up this morning.", tip(forecast(Sky.RAIN, Sky.CLOUDY, temperature = 0.5)))
    }

    @Test
    fun rainNowSaysWhenItDriesUp() {
        assertEquals("Take an umbrella. It dries up this morning.", tip(forecast(*skies(Sky.RAIN to 2, Sky.CLOUDY to 3))))
        assertEquals("Take an umbrella. It dries up after lunch.", tip(forecast(*skies(Sky.DRIZZLE to 5, Sky.CLOUDY to 3))))
        assertEquals("Take an umbrella. It dries up this afternoon.", tip(forecast(*skies(Sky.RAIN to 8, Sky.CLEAR to 3))))
        assertEquals("Take an umbrella. It dries up this evening.", tip(forecast(*skies(Sky.RAIN to 11, Sky.CLEAR to 1))))
    }

    @Test
    fun rainThatDoesNotStopWithinTheHoursSaysSo() {
        assertEquals("Take an umbrella. It isn't going anywhere soon.", tip(forecast(*skies(Sky.RAIN to 12))))
    }

    @Test
    fun rainLaterSaysWhenItArrives() {
        assertEquals("Take an umbrella. Rain's due this morning.", tip(forecast(*skies(Sky.CLEAR to 2, Sky.RAIN to 2))))
        assertEquals("Take an umbrella. Rain's due this afternoon.", tip(forecast(*skies(Sky.CLOUDY to 8, Sky.DRIZZLE to 2))))
    }

    @Test
    fun anHourWithAGoodChanceOfRainCountsAsWet() {
        val likely = forecast(Sky.CLOUDY, Sky.CLOUDY, rainChance = 60)
        val unlikely = forecast(Sky.CLOUDY, Sky.CLOUDY, rainChance = 59)
        assertEquals("Take an umbrella. It isn't going anywhere soon.", tip(likely))
        assertNull(tip(unlikely))
    }

    @Test
    fun rainThatHasAlreadyGoneByIsNotMentioned() {
        val earlier = forecast(Sky.RAIN, Sky.CLEAR, Sky.CLEAR)
        assertEquals("Take an umbrella. It dries up this morning.", tip(earlier, at = seven + 10.minutes))
        assertEquals("A lovely clear one. Get outside if you can.", tip(earlier.copy(now = earlier.now.copy(sky = Sky.CLEAR)), at = seven + 1.hours + 10.minutes))
    }

    @Test
    fun rainBeatsHeatAndCold() {
        assertEquals("Take an umbrella. It dries up this morning.", tip(forecast(Sky.RAIN, Sky.CLEAR, high = 33.0)))
        assertEquals("Take an umbrella. It dries up this morning.", tip(forecast(Sky.RAIN, Sky.CLEAR, high = 2.0, temperature = 1.0)))
    }

    @Test
    fun aVeryHotDayBeatsColdAndSunshine() {
        assertEquals("Hot one today. Water, shade and sun cream.", tip(forecast(Sky.CLEAR, high = 30.0)))
        assertEquals("Hot one today. Water, shade and sun cream.", tip(forecast(Sky.CLEAR, high = 31.0, temperature = -1.0)))
        assertEquals("A lovely clear one. Get outside if you can.", tip(forecast(Sky.CLEAR, high = 29.9)))
    }

    @Test
    fun aColdDayBeatsSunshine() {
        assertEquals("Properly cold today. Coat, hat and gloves.", tip(forecast(Sky.CLEAR, high = 8.0, temperature = 3.0)))
        assertEquals("A lovely clear one. Get outside if you can.", tip(forecast(Sky.CLEAR, high = 8.1, temperature = 3.0)))
    }

    @Test
    fun aFreezingMorningIsColdWhateverTheHigh() {
        assertEquals("Properly cold today. Coat, hat and gloves.", tip(forecast(Sky.CLEAR, high = 12.0, temperature = 0.0)))
        assertEquals("A lovely clear one. Get outside if you can.", tip(forecast(Sky.CLEAR, high = 12.0, temperature = 0.1)))
    }

    @Test
    fun aSunnyDayHasAnUpbeatTip() {
        assertEquals("A lovely clear one. Get outside if you can.", tip(forecast(*skies(Sky.CLEAR to 12))))
        assertEquals("Bright spells today. Sunglasses in the bag.", tip(forecast(*skies(Sky.PARTLY_CLOUDY to 12))))
    }

    @Test
    fun aGreyDryDayHasNothingToSay() {
        assertNull(tip(forecast(*skies(Sky.CLOUDY to 12))))
        assertNull(tip(forecast(*skies(Sky.FOG to 3, Sky.CLOUDY to 9))))
    }

    @Test
    fun partsOfTheDayFollowThePhoneNotThePlace() {
        val rain = forecast(*skies(Sky.RAIN to 5, Sky.CLOUDY to 3))
        val tokyo = TimeZone.of("Asia/Tokyo")
        assertEquals("Take an umbrella. It dries up after lunch.", tip(rain, zone = london))
        assertEquals("Take an umbrella. It dries up this evening.", tip(rain, zone = tokyo))
    }

    @Test
    fun withTheForecastsHoursGoneByItTellsOnlyOfNowAndTheDay() {
        // Fetched yesterday, so today is its second day.
        val old = forecast(Sky.CLEAR, Sky.CLEAR, fetchedAt = now - 20.hours).let { it.copy(tomorrow = it.today.copy(high = 33.0)) }
        assertEquals("Hot one today. Water, shade and sun cream.", tip(old, at = seven + 5.hours))
        assertEquals("A lovely clear one. Get outside if you can.", tip(old.copy(tomorrow = old.today.copy(high = 20.0)), at = seven + 5.hours))
    }

    @Test
    fun yesterdaysHighIsNotTakenForTodays() {
        val old = forecast(Sky.CLEAR, Sky.CLEAR, high = 33.0, fetchedAt = now - 20.hours)
        assertEquals("A lovely clear one. Get outside if you can.", tip(old, at = seven + 5.hours))
    }

    // The day line

    @Test
    fun theDayLineSaysWhenRainStops() {
        assertEquals("Rain until about 11:00", line(forecast(*skies(Sky.RAIN to 4, Sky.CLOUDY to 3))))
        assertEquals("Drizzle until about 09:00", line(forecast(Sky.DRIZZLE, Sky.DRIZZLE, Sky.CLEAR)))
        assertEquals("Storms until about 15:00", line(forecast(*skies(Sky.STORM to 8, Sky.CLOUDY to 2))))
        assertEquals("Snow until about 10:00", line(forecast(Sky.SNOW, Sky.SNOW, Sky.SNOW, Sky.CLEAR)))
    }

    @Test
    fun theDayLineNamesWhatIsFallingNowEvenIfSomethingElseFollows() {
        assertEquals("Rain until about 10:00", line(forecast(Sky.RAIN, Sky.STORM, Sky.SNOW, Sky.CLOUDY)))
    }

    @Test
    fun theDayLineWhenItNeverStopsDoesNotGiveATime() {
        assertEquals("Rain most of the day", line(forecast(*skies(Sky.RAIN to 12))))
    }

    @Test
    fun theDayLineForFogSaysWhenItLifts() {
        assertEquals("Fog lifting by 10:00", line(forecast(Sky.FOG, Sky.FOG, Sky.FOG, Sky.CLOUDY)))
        assertEquals("Fog most of the day", line(forecast(*skies(Sky.FOG to 12))))
    }

    @Test
    fun theDayLineForRainLaterSaysWhenItStarts() {
        assertEquals("Rain from about 16:00", line(forecast(*skies(Sky.CLEAR to 9, Sky.RAIN to 2))))
        assertEquals("Snow from about 09:00", line(forecast(Sky.CLOUDY, Sky.CLOUDY, Sky.SNOW)))
    }

    @Test
    fun theDayLineForADryDay() {
        assertEquals("Dry all day", line(forecast(*skies(Sky.CLEAR to 12))))
        assertEquals("Dry all day", line(forecast(*skies(Sky.PARTLY_CLOUDY to 12))))
        assertEquals("Dry under grey skies", line(forecast(*skies(Sky.CLOUDY to 12))))
    }

    @Test
    fun theDayLineUsesThePhonesClock() {
        val rain = forecast(*skies(Sky.RAIN to 4, Sky.CLOUDY to 3))
        assertEquals("Rain until about 11:00", line(rain, zone = london))
        assertEquals("Rain until about 19:00", line(rain, zone = TimeZone.of("Asia/Tokyo")))
    }

    @Test
    fun theDayLineIsSilentOnceTheHoursHaveGoneBy() {
        val old = forecast(Sky.RAIN, Sky.RAIN, fetchedAt = now - 20.hours)
        assertNull(line(old, at = seven + 5.hours))
    }

    @Test
    fun theHourAfterTheCurrentOneIsStillAhead() {
        val rain = forecast(Sky.RAIN, Sky.CLEAR)
        assertEquals("Rain until about 08:00", line(rain, at = seven + 59.minutes))
        assertEquals("Dry all day", line(rain, at = seven + 61.minutes))
    }

    // Current conditions

    @Test
    fun aFreshForecastIsTrustedForNow() {
        val fresh = forecast(Sky.CLOUDY, Sky.CLOUDY, temperature = 10.0, current = Sky.RAIN, hourTemperatures = listOf(9.0, 9.0))
        assertEquals(CurrentWeather(10.0, Sky.RAIN, isDay = true), fresh.conditionsAt(now + 59.minutes))
    }

    @Test
    fun anOlderForecastGivesItsHourForTheTimeItIs() {
        val earlier = now - 3.hours
        val old = forecast(Sky.CLEAR, Sky.CLOUDY, Sky.RAIN, temperature = 5.0, fetchedAt = earlier, hourTemperatures = listOf(5.0, 8.0, 11.0))
        assertEquals(CurrentWeather(11.0, Sky.RAIN, isDay = true), old.conditionsAt(seven + 2.hours + 30.minutes))
    }

    @Test
    fun anOlderForecastWithNoHourForNowKnowsNothingOfNow() {
        val old = forecast(Sky.CLEAR, temperature = 5.0, fetchedAt = now - 10.hours)
        assertNull(old.conditionsAt(seven + 4.hours))
    }

    @Test
    fun aFreshForecastKnowsNowEvenWithoutHours() {
        val fresh = forecast(temperature = 5.0).copy(hours = emptyList())
        assertEquals(fresh.now, fresh.conditionsAt(now + 30.minutes))
    }

    @Test
    fun anHourFromAnOldForecastIsDaylightByThePlacesClock() {
        val night = LocalDateTime(2026, 10, 12, 3, 0).toInstant(london)
        val old = forecast(Sky.CLEAR, fetchedAt = now - 5.hours).copy(hours = listOf(HourWeather(night, 6.0, Sky.CLEAR, 0)))
        assertEquals(false, old.conditionsAt(night + 30.minutes)?.isDay)
        assertEquals(true, old.copy(hours = listOf(HourWeather(seven, 6.0, Sky.CLEAR, 0)), fetchedAt = seven - 4.hours).conditionsAt(now)?.isDay)
    }
}
