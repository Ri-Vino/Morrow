package app.morrow.ui.components

import app.morrow.theme.ThemeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipPokeTest {

    @Test
    fun pipStartsAndEndsWhereItWas() {
        for (theme in ThemeKind.entries) {
            for (moon in listOf(false, true)) {
                assertEquals(PokeFrame(), pipPoke(theme, moon, 0f), "$theme start")
                assertEquals(PokeFrame(), pipPoke(theme, moon, 1f), "$theme end")
            }
        }
    }

    @Test
    fun eachTimeOfDayAnswersInItsOwnWay() {
        val morning = pipPoke(ThemeKind.MORNING, moon = false, 0.5f)
        assertTrue(morning.dy < -20f, "The morning sun hops up, was ${morning.dy}")
        assertTrue(morning.delighted)

        val noon = pipPoke(ThemeKind.NOON, moon = false, 0.5f)
        assertTrue(noon.rotation in 90f..359f, "The noon sun spins, was ${noon.rotation}")

        val afternoon = (1..9).map { pipPoke(ThemeKind.AFTERNOON, moon = false, it / 10f).rotation }
        assertTrue(afternoon.any { it > 5f } && afternoon.any { it < -5f }, "The afternoon sun wiggles both ways, was $afternoon")

        val night = pipPoke(ThemeKind.NIGHT, moon = true, 0.5f)
        assertFalse(night.delighted, "The moon sleeps on")
        assertEquals(1f, night.starBoost, 0.01f)
    }

    @Test
    fun theMoonRocksWhateverTheTheme() {
        val frames = (1..9).map { pipPoke(ThemeKind.AFTERNOON, moon = true, it / 10f) }
        assertTrue(frames.all { it.starBoost > 0f && !it.delighted })
    }
}
