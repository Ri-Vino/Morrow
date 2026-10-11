package app.morrow.data

import app.cash.turbine.test
import app.morrow.weather.Coordinates
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WeatherSettingsTest {
    private val settings = MorrowSettings(InMemoryPreferences())

    @Test
    fun defaults() = runTest {
        assertTrue(settings.weatherEnabled.first())
        assertEquals(WeatherPlaceMode.AUTO, settings.weatherSource.first())
        assertNull(settings.units.first())
        assertFalse(settings.readWeatherAloud.first())
        assertNull(settings.voiceId.first())
        assertNull(settings.lastLocation.first())
        assertNull(settings.weatherBackoffUntil.first())
    }

    @Test
    fun turningWeatherOffAndOn() = runTest {
        settings.weatherEnabled.test {
            assertTrue(awaitItem())
            settings.setWeatherEnabled(false)
            assertFalse(awaitItem())
            settings.setWeatherEnabled(true)
            assertTrue(awaitItem())
        }
    }

    @Test
    fun weatherSourceRoundTrips() = runTest {
        settings.setWeatherSource(WeatherPlaceMode.HOME)
        assertEquals(WeatherPlaceMode.HOME, settings.weatherSource.first())
        settings.setWeatherSource(WeatherPlaceMode.AUTO)
        assertEquals(WeatherPlaceMode.AUTO, settings.weatherSource.first())
    }

    @Test
    fun unitsFollowTheCountryUntilPicked() = runTest {
        assertEquals(Units.FAHRENHEIT, settings.effectiveUnits("US").first())
        assertEquals(Units.CELSIUS, settings.effectiveUnits("GB").first())

        settings.effectiveUnits("US").test {
            assertEquals(Units.FAHRENHEIT, awaitItem())
            settings.setUnits(Units.CELSIUS)
            assertEquals(Units.CELSIUS, awaitItem())
            settings.setUnits(null)
            assertEquals(Units.FAHRENHEIT, awaitItem())
        }
        settings.setUnits(Units.FAHRENHEIT)
        assertEquals(Units.FAHRENHEIT, settings.units.first())
        assertEquals(Units.FAHRENHEIT, settings.effectiveUnits("GB").first())
    }

    @Test
    fun voiceSettingsRoundTrip() = runTest {
        settings.setReadWeatherAloud(true)
        settings.setVoiceId("en-gb-x-rjs-local")
        assertTrue(settings.readWeatherAloud.first())
        assertEquals("en-gb-x-rjs-local", settings.voiceId.first())

        settings.setVoiceId(null)
        assertNull(settings.voiceId.first())
    }

    @Test
    fun theLastLocationIsStoredRounded() = runTest {
        val at = Instant.parse("2026-10-11T07:40:00Z")
        settings.saveLastLocation(Coordinates(51.507351, -0.127758), at)
        assertEquals(LastLocation(Coordinates(51.51, -0.13), at), settings.lastLocation.first())

        settings.clearLastLocation()
        assertNull(settings.lastLocation.first())
    }

    @Test
    fun theBackoffRoundTrips() = runTest {
        val until = Instant.parse("2026-10-11T08:40:00Z")
        settings.setWeatherBackoffUntil(until)
        assertEquals(until, settings.weatherBackoffUntil.first())
        settings.setWeatherBackoffUntil(null)
        assertNull(settings.weatherBackoffUntil.first())
    }
}
