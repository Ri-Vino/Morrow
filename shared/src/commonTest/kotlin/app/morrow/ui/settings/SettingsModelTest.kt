package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.IosPermissions
import app.morrow.platform.VoiceInfo
import app.morrow.theme.ThemeKind
import app.morrow.weather.Coordinates
import app.morrow.weather.HerePlace
import app.morrow.weather.HereSource
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        val section = weatherSection(enabled = true, WeatherPlaceMode.AUTO, here = leeds, homeCity = "London", Units.CELSIUS, readAloud = false)

        assertEquals("Your rough location while Morrow is open, or the last place it knew. Now: Leeds.", section.description)
        assertEquals("London", section.homeCityName)
        assertEquals(Units.CELSIUS, section.units)
        assertTrue(section.enabled)
    }

    @Test
    fun whereIAmFallingBackToTheHomeCityNamesIt() {
        val home = HerePlace(Coordinates(51.51, -0.13), "London", HereSource.HOME_CITY)

        val section = weatherSection(enabled = true, WeatherPlaceMode.AUTO, here = home, homeCity = "London", Units.CELSIUS, readAloud = false)

        assertEquals("Your rough location while Morrow is open, or the last place it knew. Now: London.", section.description)
    }

    @Test
    fun whereIAmBeforeAnyPlaceIsKnownSaysNoMore() {
        val section = weatherSection(enabled = true, WeatherPlaceMode.AUTO, here = null, homeCity = null, Units.FAHRENHEIT, readAloud = false)

        assertEquals("Your rough location while Morrow is open, or the last place it knew.", section.description)
        assertEquals("None yet", section.homeCityName)
    }

    @Test
    fun homeCityModeIsAlwaysTheHomeCity() {
        val section = weatherSection(enabled = true, WeatherPlaceMode.HOME, here = leeds, homeCity = "London", Units.CELSIUS, readAloud = false)

        assertEquals("Always London, wherever you are.", section.description)
    }

    @Test
    fun weatherChoicesReadAsTheDesignSays() {
        assertEquals(listOf("Where I am", "Home city"), WeatherPlaceMode.entries.map { it.displayName })
        assertEquals(listOf("°C", "°F"), Units.entries.map { it.symbol })
    }

    @Test
    fun readingTheWeatherAloudFollowsTheSetting() {
        fun section(readAloud: Boolean) = weatherSection(true, WeatherPlaceMode.AUTO, here = null, homeCity = null, Units.CELSIUS, readAloud)

        assertFalse(section(readAloud = false).readAloud)
        assertTrue(section(readAloud = true).readAloud)
    }

    private val british = VoiceInfo("gb-1", "English (UK) · Voice 1", "English (UK)")
    private val britishToo = VoiceInfo("gb-2", "English (UK) · Voice 2", "English (UK)")
    private val american = VoiceInfo("us", "English (US)", "English (US)")

    @Test
    fun theDefaultVoiceNamesTheLanguageItSpeaks() {
        val section = voiceSection(voiceId = null, listOf(british, britishToo, american))

        assertEquals("Phone’s default · English (UK)", section.current)
        assertNull(section.emptyNote)
    }

    @Test
    fun thePickerListsTheDefaultFirstThenEveryVoice() {
        val section = voiceSection(voiceId = null, listOf(british, britishToo, american))

        assertEquals(
            listOf(
                VoiceChoice(null, "Phone’s default", "English (UK)", selected = true),
                VoiceChoice("gb-1", "English (UK) · Voice 1", null, selected = false),
                VoiceChoice("gb-2", "English (UK) · Voice 2", null, selected = false),
                VoiceChoice("us", "English (US)", null, selected = false),
            ),
            section.choices,
        )
    }

    @Test
    fun aPickedVoiceIsNamedAndTheOnlyOneSelected() {
        val section = voiceSection(voiceId = "gb-2", listOf(british, britishToo, american))

        assertEquals("English (UK) · Voice 2", section.current)
        assertEquals(listOf("gb-2"), section.choices.filter { it.selected }.map { it.id })
    }

    @Test
    fun aVoiceThatIsNoLongerInstalledFallsBackToTheDefault() {
        val section = voiceSection(voiceId = "removed", listOf(british, american))

        assertEquals("Phone’s default · English (UK)", section.current)
        assertEquals(listOf<String?>(null), section.choices.filter { it.selected }.map { it.id })
    }

    @Test
    fun withNoVoicesTheRowSaysSoAndThePickerOffersOnlyTheDefault() {
        val section = voiceSection(voiceId = "gb-1", emptyList())

        assertEquals("No voices on this phone", section.current)
        assertEquals(listOf(VoiceChoice(null, "Phone’s default", null, selected = true)), section.choices)
        assertEquals("This phone has no voice for Pip, so Pip stays quiet. The words stay on screen.", section.emptyNote)
    }

    @Test
    fun whileTheVoicesAreLoadingItSaysJustTheDefault() {
        val section = voiceSection(voiceId = null, voices = null)

        assertEquals("Phone’s default", section.current)
        assertNull(section.emptyNote)
        assertEquals(listOf(VoiceChoice(null, "Phone’s default", null, selected = true)), section.choices)
    }

    @Test
    fun theSampleSentencesAreTheDesigns() {
        assertEquals("Good morning! It’s twelve degrees and rainy in London. Take an umbrella.", VOICE_SAMPLE_PHRASE)
        assertEquals("Hello, I’m Pip. This is how I’ll sound.", VOICE_PICKED_PHRASE)
    }
}
