package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.IosPermissions
import app.morrow.theme.ThemeKind
import app.morrow.weather.Coordinates
import app.morrow.weather.HerePlace
import app.morrow.weather.HereSource
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode
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
        assertEquals(listOf(true, false, true, true, true), model.permissions.map { it.granted })
    }

    @Test
    fun locationIsListedOnAndroid() {
        val model = settingsModel(null, ThemeKind.NOON, Difficulty.NORMAL, AndroidPermissions, missing = listOf(AlarmPermission.LOCATION))
        assertEquals(PermissionStatus(AlarmPermission.LOCATION, granted = false), model.permissions.last())
    }

    @Test
    fun listsOnlyThePlatformsPermissions() {
        val model = settingsModel(null, ThemeKind.NOON, Difficulty.NORMAL, IosPermissions, missing = emptyList())
        assertEquals(listOf(PermissionStatus(AlarmPermission.ALARMS, granted = true)), model.permissions)
    }

    private val leeds = HerePlace(Coordinates(53.8, -1.55), "Leeds", HereSource.LOCATION)

    @Test
    fun whereIAmSaysWhichPlaceItIsUsingNow() {
        val section = weatherSection(enabled = true, WeatherPlaceMode.AUTO, here = leeds, homeCity = "London", Units.CELSIUS)

        assertEquals("Your rough location while Morrow is open, or the last place it knew. Now: Leeds.", section.description)
        assertEquals("London", section.homeCityName)
        assertEquals(Units.CELSIUS, section.units)
        assertTrue(section.enabled)
    }

    @Test
    fun whereIAmFallingBackToTheHomeCityNamesIt() {
        val home = HerePlace(Coordinates(51.51, -0.13), "London", HereSource.HOME_CITY)

        val section = weatherSection(enabled = true, WeatherPlaceMode.AUTO, here = home, homeCity = "London", Units.CELSIUS)

        assertEquals("Your rough location while Morrow is open, or the last place it knew. Now: London.", section.description)
    }

    @Test
    fun whereIAmBeforeAnyPlaceIsKnownSaysNoMore() {
        val section = weatherSection(enabled = true, WeatherPlaceMode.AUTO, here = null, homeCity = null, Units.FAHRENHEIT)

        assertEquals("Your rough location while Morrow is open, or the last place it knew.", section.description)
        assertEquals("None yet", section.homeCityName)
    }

    @Test
    fun homeCityModeIsAlwaysTheHomeCity() {
        val section = weatherSection(enabled = true, WeatherPlaceMode.HOME, here = leeds, homeCity = "London", Units.CELSIUS)

        assertEquals("Always London, wherever you are.", section.description)
    }

    @Test
    fun weatherChoicesReadAsTheDesignSays() {
        assertEquals(listOf("Where I am", "Home city"), WeatherPlaceMode.entries.map { it.displayName })
        assertEquals(listOf("°C", "°F"), Units.entries.map { it.symbol })
    }
}
