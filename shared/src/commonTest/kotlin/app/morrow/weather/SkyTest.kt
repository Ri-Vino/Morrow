package app.morrow.weather

import kotlin.test.Test
import kotlin.test.assertEquals

class SkyTest {

    @Test
    fun everyWmoCodeInTheSpecTableMapsToItsSky() {
        val table = mapOf(
            Sky.CLEAR to listOf(0),
            Sky.PARTLY_CLOUDY to listOf(1, 2),
            Sky.CLOUDY to listOf(3),
            Sky.FOG to listOf(45, 48),
            Sky.DRIZZLE to (51..57).toList(),
            Sky.RAIN to (61..67).toList() + (80..82).toList(),
            Sky.SNOW to (71..77).toList() + listOf(85, 86),
            Sky.STORM to (95..99).toList(),
        )
        for ((sky, codes) in table) {
            for (code in codes) assertEquals(sky, skyForWmo(code), "WMO $code")
        }
    }

    @Test
    fun unknownCodesReadAsCloudy() {
        for (code in listOf(-1, 4, 10, 44, 46, 50, 58, 60, 68, 70, 78, 79, 83, 84, 87, 94, 100, 999)) {
            assertEquals(Sky.CLOUDY, skyForWmo(code), "WMO $code")
        }
    }
}
