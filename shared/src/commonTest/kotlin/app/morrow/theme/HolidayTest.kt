package app.morrow.theme

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HolidayTest {
    private fun on(text: String) = Holiday.on(LocalDate.parse(text))

    @Test
    fun halloweenIsTheWeekUpToTheThirtyFirst() {
        assertNull(on("2026-10-23"))
        assertEquals(Holiday.HALLOWEEN, on("2026-10-24"))
        assertEquals(Holiday.HALLOWEEN, on("2026-10-30"))
        assertEquals(Holiday.HALLOWEEN_NIGHT, on("2026-10-31"))
        assertNull(on("2026-11-01"))
    }

    @Test
    fun christmasRunsThroughDecemberAndNewYearTakesOver() {
        assertNull(on("2026-11-30"))
        assertEquals(Holiday.CHRISTMAS, on("2026-12-01"))
        assertEquals(Holiday.CHRISTMAS, on("2026-12-25"))
        assertEquals(Holiday.CHRISTMAS, on("2026-12-30"))
        assertEquals(Holiday.NEW_YEAR, on("2026-12-31"))
        assertEquals(Holiday.NEW_YEAR, on("2027-01-01"))
        assertNull(on("2027-01-02"))
    }

    @Test
    fun aForcedHolidayShowsOnAnyDayAndWhateverSettingsSays() {
        val ordinary = LocalDateTime.parse("2026-06-10T08:00").toInstant(TimeZone.UTC)
        assertNull(Holiday.at(ordinary, TimeZone.UTC, seasonal = true))
        Holiday.forced = Holiday.CHRISTMAS
        try {
            assertEquals(Holiday.CHRISTMAS, Holiday.at(ordinary, TimeZone.UTC, seasonal = true))
            assertEquals(Holiday.CHRISTMAS, Holiday.at(ordinary, TimeZone.UTC, seasonal = false))
        } finally {
            Holiday.forced = null
        }
        assertNull(Holiday.at(ordinary, TimeZone.UTC, seasonal = true))
    }
}
