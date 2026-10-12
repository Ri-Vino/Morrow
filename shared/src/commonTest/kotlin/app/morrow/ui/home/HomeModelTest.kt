package app.morrow.ui.home

import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.platform.AlarmPermission
import app.morrow.weather.Coordinates
import app.morrow.weather.HerePlace
import app.morrow.weather.HereSource
import app.morrow.weather.HourWeather
import app.morrow.weather.PlaceWeather
import app.morrow.weather.Sky
import app.morrow.weather.Units
import app.morrow.weather.WeatherAge
import app.morrow.weather.WeatherState
import app.morrow.weather.forecastFor
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
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
import kotlin.time.Instant

private val london = TimeZone.of("Europe/London")
private val weekdays = setOf(MONDAY, DayOfWeek.TUESDAY, WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY)

// 2026-10-06 is a Tuesday.
class HomeModelTest {

    private fun alarm(
        hour: Int,
        minute: Int,
        label: String = "Work days",
        days: Set<DayOfWeek> = emptySet(),
        mission: MissionType = MissionType.SUMS,
        enabled: Boolean = true,
        id: Long = 1,
        more: List<MissionType> = emptyList(),
        zone: String? = null,
        city: String? = null,
    ) = Alarm(id, hour, minute, label, days, (listOf(mission) + more).map { GameStep(it) }, enabled, zone, city)

    private fun home(at: String, vararg alarms: Alarm, missing: List<AlarmPermission> = emptyList()) =
        homeModel(alarms.toList(), LocalDateTime.parse(at).toInstant(london), london, missing)

    @Test
    fun greetingFollowsTheTimeOfDay() {
        assertEquals("Still up", home("2026-10-06T01:20").greeting)
        assertEquals("Good morning", home("2026-10-06T07:40").greeting)
        assertEquals("Good afternoon", home("2026-10-06T12:30").greeting)
        assertEquals("Good evening", home("2026-10-06T18:00").greeting)
        assertEquals("Good night", home("2026-10-06T21:40").greeting)
    }

    @Test
    fun theMorningAnswerShowsUnderTheGreeting() {
        val at = LocalDateTime.parse("2026-10-08T08:00").toInstant(london)
        assertEquals("Looking forward to coffee by the window", homeModel(emptyList(), at, london, emptyList(), "coffee by the window").lookingForward)
        assertEquals(null, homeModel(emptyList(), at, london, emptyList(), "  ").lookingForward)
        assertEquals(null, home("2026-10-08T08:00").lookingForward)
    }

    @Test
    fun dateLabelSpellsOutTheDay() {
        assertEquals("Tuesday 6 October", home("2026-10-06T07:40").dateLabel)
    }

    @Test
    fun noAlarmsMeansNoSleepCardAndAnEmptyList() {
        val model = home("2026-10-06T21:40")
        assertNull(model.sleepCard)
        assertTrue(model.rows.isEmpty())
        assertEquals("", model.alarmCount)
    }

    @Test
    fun allAlarmsOffLetsPipSleepIn() {
        val card = home("2026-10-06T21:40", alarm(6, 30, enabled = false)).sleepCard!!
        assertEquals(SleepCard("No alarms are on", "Sleep in", "Pip will let you rest. Switch an alarm on when you need one.", night = false), card)
    }

    @Test
    fun atBedtimeTheCardCountsSleep() {
        val card = home("2026-10-06T23:10", alarm(6, 30, days = weekdays)).sleepCard!!
        assertEquals("Sleep now and you’ll get", card.lead)
        assertEquals("7h 20m", card.big)
        assertEquals("That’s about 4 full sleep cycles. Pip wakes you tomorrow at 06:30.", card.detail)
        assertTrue(card.night)
    }

    @Test
    fun oneSleepCycleIsSingular() {
        val card = home("2026-10-06T23:00", alarm(0, 45)).sleepCard!!
        assertEquals("That’s about 1 full sleep cycle. Pip wakes you tomorrow at 00:45.", card.detail)
    }

    @Test
    fun duringTheDayTheCardCountsDown() {
        val card = home("2026-10-06T13:00", alarm(15, 30, label = "Gym", mission = MissionType.SCAN)).sleepCard!!
        assertEquals("Pip wakes you in", card.lead)
        assertEquals("2h 30m", card.big)
        assertEquals("Gym, today at 15:30 · Scan", card.detail)
    }

    @Test
    fun underAnHourShowsMinutes() {
        assertEquals("45 min", home("2026-10-06T13:00", alarm(13, 45)).sleepCard!!.big)
        assertEquals("1h 05m", home("2026-10-06T13:00", alarm(14, 5)).sleepCard!!.big)
    }

    @Test
    fun partMinutesRoundUpSoTheCountMatchesTheClock() {
        // The minute ticker fires a few milliseconds after the minute starts.
        assertEquals("7h 20m", home("2026-10-06T23:10:00.005", alarm(6, 30)).sleepCard!!.big)
        assertEquals("1 min", home("2026-10-06T04:51:50", alarm(4, 52)).sleepCard!!.big)
    }

    @Test
    fun aWakeUpMoreThanADayAwayShowsTheDay() {
        val card = home("2026-10-06T08:00", alarm(6, 30, label = "Work", days = setOf(MONDAY))).sleepCard!!
        assertEquals("Next wake-up", card.lead)
        assertEquals("Mon 06:30", card.big)
        assertEquals("Work · Sums. Pip will be up before you.", card.detail)
    }

    @Test
    fun lostExactAlarmsShowsTheBannerAndPausesTheCard() {
        val model = home("2026-10-06T23:10", alarm(6, 30), missing = listOf(AlarmPermission.EXACT_ALARMS))
        assertEquals("Morrow lost permission to set exact alarms. Nothing will go off until you allow it again.", model.banner)
        assertEquals("Paused until alarms are allowed", model.sleepCard!!.lead)
    }

    @Test
    fun iosBannerSaysAlarmsArentAllowed() {
        val model = home("2026-10-06T23:10", alarm(6, 30), missing = listOf(AlarmPermission.ALARMS))
        assertEquals("Morrow isn’t allowed to set alarms. Nothing will go off until you allow it.", model.banner)
    }

    @Test
    fun bannerNamesEveryMissingRequiredPermission() {
        val model = home(
            "2026-10-06T23:10",
            missing = listOf(AlarmPermission.NOTIFICATIONS, AlarmPermission.FULL_SCREEN, AlarmPermission.BATTERY),
        )
        assertEquals("Morrow needs notifications and full-screen alarm before any alarm can go off.", model.banner)
    }

    @Test
    fun batteryAloneIsNotABlocker() {
        assertNull(home("2026-10-06T23:10", alarm(6, 30), missing = listOf(AlarmPermission.BATTERY)).banner)
    }

    @Test
    fun rowsDescribeEachAlarm() {
        val model = home(
            "2026-10-06T21:40",
            alarm(6, 30, "Work days", weekdays, id = 1, more = listOf(MissionType.BREATHE)),
            alarm(9, 0, "Slow Sunday", setOf(SUNDAY), MissionType.BREATHE, id = 2),
            alarm(5, 45, "Gym", setOf(MONDAY, WEDNESDAY, FRIDAY), MissionType.SCAN, enabled = false, id = 3),
        )
        assertEquals("2 of 3 on", model.alarmCount)
        assertEquals(
            listOf(
                AlarmRow(1, "06:30", "Work days · Weekdays", MissionType.SUMS, "Sums +1", enabled = true, sunUp = true),
                AlarmRow(2, "09:00", "Slow Sunday · Sun", MissionType.BREATHE, "Breathe", enabled = true, sunUp = true),
                AlarmRow(3, "05:45", "Gym · Mon Wed Fri", MissionType.SCAN, "Scan", enabled = false, sunUp = false),
            ),
            model.rows,
        )
    }

    @Test
    fun repeatPatternsHaveFriendlyNames() {
        assertEquals("Every day", describeDays(DayOfWeek.entries.toSet()))
        assertEquals("Weekdays", describeDays(weekdays))
        assertEquals("Weekends", describeDays(setOf(SATURDAY, SUNDAY)))
        assertEquals("Once", describeDays(emptySet()))
        assertEquals("Mon Sat", describeDays(setOf(SATURDAY, MONDAY)))
    }

    @Test
    fun aZonedRowSaysWhoseClockItKeepsAndWhenItRingsHere() {
        val model = home(
            "2026-10-06T07:40",
            alarm(9, 0, "Stand-up", weekdays, zone = "America/New_York", city = "New York", id = 1),
            alarm(7, 0, id = 2),
        )
        assertEquals("09:00", model.rows[0].time)
        assertEquals("New York time · 14:00 here", model.rows[0].zoneText)
        assertNull(model.rows[1].zoneText)
    }

    @Test
    fun theSleepCardUsesWhenAZonedAlarmReallyRings() {
        // 08:00 in Tokyo is midnight in London.
        val card = home("2026-10-06T22:00", alarm(8, 0, "Flight", zone = "Asia/Tokyo", city = "Tokyo")).sleepCard
        assertEquals("2h 00m", card?.big)
        assertTrue(card!!.detail.endsWith("Pip wakes you tomorrow at 00:00."), card.detail)
    }

    private val place = Coordinates(51.51, -0.13)

    private fun here(at: Instant, fetched: Instant = at - 5.minutes, sky: Sky = Sky.RAIN, name: String? = "London") = WeatherState(
        enabled = true,
        here = HerePlace(place, name, HereSource.LOCATION),
        hereWeather = PlaceWeather(place, forecastFor(place, fetched, temperature = 12.0, sky = sky), WeatherAge.Fresh(5)),
        cities = emptyMap(),
    )

    private fun line(at: String, state: (Instant) -> WeatherState? = { here(it) }, units: Units = Units.CELSIUS): HomeWeather? {
        val now = LocalDateTime.parse(at).toInstant(london)
        return homeWeather(state(now), now, london, units)
    }

    @Test
    fun theWeatherLineSaysTheTemperatureTheConditionAndWhere() {
        val line = line("2026-10-06T07:40")!!
        assertEquals("12° · Rain in London", line.text)
        assertEquals(Sky.RAIN, line.sky)
        assertFalse(line.night)
    }

    @Test
    fun screenReadersHearTheWeatherAndThatItOpensWorld() {
        assertEquals("12° and rain in London. Open World", line("2026-10-06T07:40")?.label)
    }

    @Test
    fun theWeatherLineCanBeInFahrenheit() {
        assertEquals("54° · Rain in London", line("2026-10-06T07:40", units = Units.FAHRENHEIT)?.text)
    }

    @Test
    fun aPlaceWithoutANameLeavesItOut() {
        val line = line("2026-10-06T07:40", { here(it, name = null) })
        assertEquals("12° · Rain", line?.text)
        assertEquals("12° and rain. Open World", line?.label)
    }

    @Test
    fun aClearNightIsMarkedForTheMoonIcon() {
        val line = line("2026-10-06T23:00", { here(it, sky = Sky.CLEAR) })
        assertEquals("12° · Clear in London", line?.text)
        assertTrue(line!!.night)
    }

    @Test
    fun theLineHidesWithWeatherOffMissingOrOverADayOld() {
        assertNull(line("2026-10-06T07:40", { null }))
        assertNull(line("2026-10-06T07:40", { WeatherState.Off }))
        assertNull(line("2026-10-06T07:40", { here(it, fetched = it - 24.hours - 1.minutes) }))
        assertNull(line("2026-10-06T07:40", { here(it).copy(hereWeather = PlaceWeather(place, null, WeatherAge.Expired)) }))
        assertNull(line("2026-10-06T07:40", { here(it).copy(hereWeather = null) }))
    }

    @Test
    fun oldWeatherShowsTheForecastForThisHour() {
        val line = line("2026-10-06T07:40", { now ->
            val fetched = now - 5.hours
            val hourly = forecastFor(place, fetched, temperature = 9.0, sky = Sky.CLOUDY).copy(
                hours = List(12) { i -> HourWeather(fetched + i.hours, 9.0 + i, if (i == 5) Sky.RAIN else Sky.CLOUDY, rainChance = 10) },
            )
            here(now).copy(hereWeather = PlaceWeather(place, hourly, WeatherAge.Old(5)))
        })
        assertEquals("14° · Rain in London", line?.text)
    }

    @Test
    fun theLineHidesOnceAnOldForecastsHoursRunOut() {
        assertNull(line("2026-10-06T07:40", { here(it, fetched = it - 23.hours) }))
    }

    @Test
    fun homeCarriesTheLine() {
        val now = LocalDateTime.parse("2026-10-06T07:40").toInstant(london)
        val line = homeWeather(here(now), now, london, Units.CELSIUS)
        assertEquals(line, homeModel(emptyList(), now, london, emptyList(), weather = line).weather)
        assertNull(home("2026-10-06T07:40").weather)
    }
}
