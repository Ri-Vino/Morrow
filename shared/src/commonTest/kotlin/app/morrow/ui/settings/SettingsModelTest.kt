package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.theme.ThemeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModelTest {

    @Test
    fun followingTheClockShowsTheClocksTheme() {
        val model = settingsModel(pinned = null, byClock = ThemeKind.NOON, Difficulty.NORMAL, emptyList())
        assertTrue(model.followClock)
        assertEquals("Now showing High noon", model.followDescription)
        assertEquals(ThemeKind.NOON, model.shownTheme)
    }

    @Test
    fun aPinnedThemeWins() {
        val model = settingsModel(pinned = ThemeKind.NIGHT, byClock = ThemeKind.NOON, Difficulty.NORMAL, emptyList())
        assertFalse(model.followClock)
        assertEquals("Pinned to Moonlit night", model.followDescription)
        assertEquals(ThemeKind.NIGHT, model.shownTheme)
    }

    @Test
    fun difficultySpellsOutEachMission() {
        assertEquals("2 sums · 3 breaths · 20 shakes", settingsModel(null, ThemeKind.NOON, Difficulty.GENTLE, emptyList()).difficultyDescription)
        assertEquals("5 sums · 3 breaths · 45 shakes", settingsModel(null, ThemeKind.NOON, Difficulty.TOUGH, emptyList()).difficultyDescription)
    }

    @Test
    fun listsEveryPermissionWithItsState() {
        val model = settingsModel(null, ThemeKind.NOON, Difficulty.NORMAL, missing = listOf(AlarmPermission.EXACT_ALARMS))
        assertEquals(AlarmPermission.entries, model.permissions.map { it.permission })
        assertEquals(listOf(true, false, true, true), model.permissions.map { it.granted })
    }
}
