package app.morrow.weather

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoordinatesTest {

    @Test
    fun roundsToTwoDecimalPlaces() {
        assertEquals(Coordinates(51.51, -0.13), Coordinates(51.507351, -0.127758).rounded())
        assertEquals(Coordinates(-33.87, 151.21), Coordinates(-33.868820, 151.209290).rounded())
        assertEquals(Coordinates(0.0, 0.0), Coordinates(0.004, -0.004).rounded())
    }

    @Test
    fun keyIsTheRoundedPlaceWithExactlyTwoDecimals() {
        assertEquals("51.51,-0.13", Coordinates(51.507351, -0.127758).key)
        assertEquals("40.00,-74.10", Coordinates(40.0, -74.1).key)
        assertEquals("0.00,0.00", Coordinates(-0.001, 0.001).key)
        assertEquals("-0.50,179.99", Coordinates(-0.5, 179.991).key)
    }

    @Test
    fun aForecastUrlNeverHasMoreThanTwoDecimals() {
        val random = Random(17)
        val places = List(200) { Coordinates(random.nextDouble(-90.0, 90.0), random.nextDouble(-180.0, 180.0)) } +
            listOf(Coordinates(0.1 + 0.2, 1.0 / 3), Coordinates(-0.005, 89.999999), Coordinates(1e-9, -1e-9))
        val url = OpenMeteoSource.forecastUrl(places)
        assertFalse(Regex("""\d\.\d{3,}""").containsMatchIn(url), url)
        assertFalse(Regex("""\d[eE]""").containsMatchIn(url), "no exponent notation: $url")
    }

    @Test
    fun roundingRandomPlacesNeverLeavesMoreThanTwoDecimals() {
        val random = Random(42)
        repeat(1000) {
            val place = Coordinates(random.nextDouble(-90.0, 90.0), random.nextDouble(-180.0, 180.0)).rounded()
            for (part in place.key.split(",")) assertEquals(2, part.substringAfter('.').length, place.key)
            assertEquals(place, place.rounded())
        }
    }
}
