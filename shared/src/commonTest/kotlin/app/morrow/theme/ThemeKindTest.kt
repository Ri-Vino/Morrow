package app.morrow.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeKindTest {

    private fun at(hour: Int, minute: Int) = ThemeKind.at(hour * 60 + minute)

    @Test
    fun boundariesFollowTheDesign() {
        assertEquals(ThemeKind.NIGHT, at(0, 0))
        assertEquals(ThemeKind.NIGHT, at(4, 59))
        assertEquals(ThemeKind.MORNING, at(5, 0))
        assertEquals(ThemeKind.MORNING, at(10, 59))
        assertEquals(ThemeKind.NOON, at(11, 0))
        assertEquals(ThemeKind.NOON, at(14, 59))
        assertEquals(ThemeKind.AFTERNOON, at(15, 0))
        assertEquals(ThemeKind.AFTERNOON, at(18, 59))
        assertEquals(ThemeKind.NIGHT, at(19, 0))
        assertEquals(ThemeKind.NIGHT, at(23, 59))
    }
}
