package app.morrow.theme

import kotlinx.datetime.LocalDate
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
}
