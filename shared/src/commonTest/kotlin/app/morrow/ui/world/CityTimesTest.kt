package app.morrow.ui.world

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class CityTimesTest {

    private val london = TimeZone.of("Europe/London")
    private val newYork = TimeZone.of("America/New_York")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val auckland = TimeZone.of("Pacific/Auckland")
    private val honolulu = TimeZone.of("Pacific/Honolulu")
    private val mumbai = TimeZone.of("Asia/Kolkata")
    private val stJohns = TimeZone.of("America/St_Johns")
    private val kathmandu = TimeZone.of("Asia/Kathmandu")

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    @Test
    fun clockShowsTheWallTimeInTheZone() {
        val now = at(london, 2026, 1, 15, 7, 40)
        assertEquals("07:40", cityClock(now, london))
        assertEquals("02:40", cityClock(now, newYork))
        assertEquals("16:40", cityClock(now, tokyo))
    }

    @Test
    fun minuteCountsFromLocalMidnight() {
        val now = at(london, 2026, 1, 15, 7, 40)
        assertEquals(7 * 60 + 40, cityMinute(now, london))
        assertEquals(2 * 60 + 40, cityMinute(now, newYork))
        assertEquals(0, cityMinute(at(tokyo, 2026, 1, 15, 0, 0), tokyo))
        assertEquals(23 * 60 + 59, cityMinute(at(tokyo, 2026, 1, 15, 23, 59), tokyo))
    }

    @Test
    fun theSameOffsetReadsAsYourTime() {
        val now = at(london, 2026, 1, 15, 7, 40)
        assertEquals("Today · your time", relativeDay(now, london, london))
        assertEquals("Today · your time", relativeDay(now, TimeZone.of("Europe/Lisbon"), london))
    }

    @Test
    fun behindThePhoneIsAMinusAndStaysToday() {
        val now = at(london, 2026, 1, 15, 12, 0)
        assertEquals("Today · −5 h", relativeDay(now, newYork, london))
    }

    @Test
    fun aheadAcrossMidnightIsTomorrow() {
        assertEquals("Tomorrow · +9 h", relativeDay(at(london, 2026, 1, 15, 20, 0), tokyo, london))
        assertEquals("Today · +9 h", relativeDay(at(london, 2026, 1, 15, 12, 0), tokyo, london))
    }

    @Test
    fun behindAcrossMidnightIsYesterday() {
        assertEquals("Yesterday · −19 h", relativeDay(at(tokyo, 2026, 1, 15, 3, 0), honolulu, tokyo))
        assertEquals("Yesterday · −5 h", relativeDay(at(london, 2026, 1, 15, 2, 0), newYork, london))
    }

    @Test
    fun halfHourOffsetsUseAHalf() {
        val now = at(london, 2026, 1, 15, 12, 0)
        assertEquals("Today · +5½ h", relativeDay(now, mumbai, london))
        assertEquals("Today · −3½ h", relativeDay(now, stJohns, london))
    }

    @Test
    fun otherOffsetsSpellOutTheMinutes() {
        val now = at(london, 2026, 1, 15, 12, 0)
        assertEquals("Today · +5 h 45 m", relativeDay(now, kathmandu, london))
    }

    @Test
    fun largeOffsetsCanSpanTwoDays() {
        assertEquals("Tomorrow · +23 h", relativeDay(at(honolulu, 2026, 1, 15, 22, 0), auckland, honolulu))
    }

    @Test
    fun daylightSavingChangesTheGapBetweenCities() {
        // The US ends daylight saving a week after Europe, so for that week London and New York are 4 h apart.
        assertEquals("Today · −4 h", relativeDay(at(london, 2026, 10, 30, 12, 0), newYork, london))
        assertEquals("Today · −5 h", relativeDay(at(london, 2026, 11, 3, 12, 0), newYork, london))
        assertEquals("Today · −5 h", relativeDay(at(london, 2026, 10, 20, 12, 0), newYork, london))
    }
}
