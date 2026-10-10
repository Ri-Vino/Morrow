package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.IosPermissions
import app.morrow.theme.ThemeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModelTest {

    @Test
    fun followingTheClockShowsTheClocksTheme() {
        val model = settingsModel(pinned = null, byClock = ThemeKind.NOON, Difficulty.NORMAL, AndroidPermissions, emptyList())
        assertTrue(model.followClock)
        assertEquals("Now showing High noon", model.followDescription)
        assertEquals(ThemeKind.NOON, model.shownTheme)
    }

    @Test
    fun aPinnedThemeWins() {
        val model = settingsModel(pinned = ThemeKind.NIGHT, byClock = ThemeKind.NOON, Difficulty.NORMAL, AndroidPermissions, emptyList())
        assertFalse(model.followClock)
        assertEquals("Pinned to Moonlit night", model.followDescription)
        assertEquals(ThemeKind.NIGHT, model.shownTheme)
    }

    @Test
    fun difficultySaysHowHardEachRoundIs() {
        fun model(difficulty: Difficulty) = settingsModel(null, ThemeKind.NOON, difficulty, AndroidPermissions, emptyList())
        assertEquals("Gentler rounds: small sums, short sentences, soft shakes.", model(Difficulty.GENTLE).difficultyLevel)
        assertEquals("Everyday rounds: two-digit sums and short sentences.", model(Difficulty.NORMAL).difficultyLevel)
        assertEquals("Tougher rounds: bigger sums, longer sentences, firm shakes.", model(Difficulty.TOUGH).difficultyLevel)
        assertEquals("How hard each round is. The number of rounds is set on each alarm.", model(Difficulty.TOUGH).difficultyDescription)
    }

    @Test
    fun listsEveryPermissionWithItsState() {
        val model = settingsModel(null, ThemeKind.NOON, Difficulty.NORMAL, AndroidPermissions, missing = listOf(AlarmPermission.EXACT_ALARMS))
        assertEquals(AndroidPermissions, model.permissions.map { it.permission })
        assertEquals(listOf(true, false, true, true), model.permissions.map { it.granted })
    }

    @Test
    fun listsOnlyThePlatformsPermissions() {
        val model = settingsModel(null, ThemeKind.NOON, Difficulty.NORMAL, IosPermissions, missing = emptyList())
        assertEquals(listOf(PermissionStatus(AlarmPermission.ALARMS, granted = true)), model.permissions)
    }
}
