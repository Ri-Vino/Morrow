package app.morrow.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WeatherRulesTest {
    private val now = Instant.parse("2026-10-11T07:40:00Z")

    @Test
    fun refreshesWhenThereIsNoCacheOrItIsOlderThanHalfAnHour() {
        assertTrue(needsRefresh(fetchedAt = null, now = now))
        assertFalse(needsRefresh(now, now))
        assertFalse(needsRefresh(now - 29.minutes, now))
        assertFalse(needsRefresh(now - 30.minutes, now))
        assertTrue(needsRefresh(now - 30.minutes - 1.milliseconds, now))
        assertTrue(needsRefresh(now - 5.hours, now))
    }

    @Test
    fun refreshesACacheFromTheFutureAfterTheClockMovesBack() {
        assertTrue(needsRefresh(now + 1.minutes, now))
    }

    @Test
    fun weatherIsFreshForThreeHoursWithMinutes() {
        assertEquals(WeatherAge.Fresh(0), weatherAge(now, now))
        assertEquals(WeatherAge.Fresh(12), weatherAge(now - 12.minutes - 30_000.milliseconds, now))
        assertEquals(WeatherAge.Fresh(180), weatherAge(now - 3.hours, now))
    }

    @Test
    fun weatherIsOldFromThreeToTwentyFourHoursWithHours() {
        assertEquals(WeatherAge.Old(3), weatherAge(now - 3.hours - 1.milliseconds, now))
        assertEquals(WeatherAge.Old(5), weatherAge(now - 5.hours - 59.minutes, now))
        assertEquals(WeatherAge.Old(24), weatherAge(now - 24.hours, now))
    }

    @Test
    fun weatherOverADayOldOrMissingHasExpired() {
        assertEquals(WeatherAge.Expired, weatherAge(now - 24.hours - 1.milliseconds, now))
        assertEquals(WeatherAge.Expired, weatherAge(now - 72.hours, now))
        assertEquals(WeatherAge.Expired, weatherAge(null, now))
    }

    @Test
    fun weatherFromTheFutureCountsAsJustUpdated() {
        assertEquals(WeatherAge.Fresh(0), weatherAge(now + 2.hours, now))
    }

    @Test
    fun aRateLimitBacksOffForAnHour() {
        val until = backoffUntil(now)
        assertEquals(now + 1.hours, until)
        assertTrue(isBackingOff(until, now + 59.minutes))
        assertFalse(isBackingOff(until, now + 1.hours))
        assertFalse(isBackingOff(null, now))
    }

    @Test
    fun theWeatherPlaceIsTheCurrentFixThenTheLastThenHomeThenTheZoneCity() {
        val current = Coordinates(51.51, -0.13)
        val last = Coordinates(53.48, -2.24)
        val home = Coordinates(55.95, -3.19)
        val zoneCity = Coordinates(51.51, -0.12)

        assertEquals(current, weatherPlace(current, last, home, zoneCity))
        assertEquals(last, weatherPlace(null, last, home, zoneCity))
        assertEquals(home, weatherPlace(null, null, home, zoneCity))
        assertEquals(zoneCity, weatherPlace(null, null, null, zoneCity))
        assertNull(weatherPlace(null, null, null, null))
        assertEquals(current, weatherPlace(current, null, null, null))
    }

    @Test
    fun homeModeSkipsTheLocationFixes() {
        val current = Coordinates(51.51, -0.13)
        val home = Coordinates(55.95, -3.19)
        val zoneCity = Coordinates(51.51, -0.12)

        assertEquals(home, weatherPlace(current, current, home, zoneCity, WeatherPlaceMode.HOME))
        assertEquals(zoneCity, weatherPlace(current, current, null, zoneCity, WeatherPlaceMode.HOME))
    }
}
