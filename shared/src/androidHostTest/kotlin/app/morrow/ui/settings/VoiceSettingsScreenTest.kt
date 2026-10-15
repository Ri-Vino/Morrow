package app.morrow.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.FakeSpeaker
import app.morrow.platform.PermissionGate
import app.morrow.platform.SpeechPurpose
import app.morrow.platform.VoiceInfo
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VoiceSettingsScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val settings = MorrowSettings(InMemoryPreferences())
    private val catalog = CityCatalog { "2643743\tLondon\t\tEngland\tUnited Kingdom\t51.5085\t-0.1257\tEurope/London\t8961989" }
    private val cities = CityRepository(FakeSavedCityDao(), catalog) { "Europe/London" }
    private val permissions = object : PermissionGate {
        override val asked = AndroidPermissions
        override val missing: StateFlow<List<AlarmPermission>> = MutableStateFlow(emptyList())
        override fun refresh() = Unit
        override fun request(permission: AlarmPermission) = Unit
    }
    private val british = VoiceInfo("gb-1", "English (UK) · Voice 1", "English (UK)")
    private val britishToo = VoiceInfo("gb-2", "English (UK) · Voice 2", "English (UK)")
    private val american = VoiceInfo("us", "English (US)", "English (US)")

    private fun show(speaker: FakeSpeaker, fontScale: Float = 1f) {
        runBlocking { cities.ensureSeeded() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MorrowTheme(ThemeKind.MORNING) {
                    SettingsRoute(
                        settings = settings,
                        permissions = permissions,
                        cities = cities,
                        here = MutableStateFlow(null),
                        phoneCountry = "GB",
                        speaker = speaker,
                        developerTools = null,
                        onPickHomeCity = {},
                        onWeatherChanged = {},
                        onBack = {},
                    )
                }
            }
        }
        waitForText("Pip’s voice")
    }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun openThePicker() {
        compose.onNodeWithText("Pip’s voice").performScrollTo().performClick()
        waitForText("Phone’s default")
    }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 5_000, condition)

    @Test
    fun theVoiceSectionShowsTheCurrentVoiceASampleButtonAndWhereTheVoiceComesFrom() {
        show(FakeSpeaker(listOf(british, american)))
        waitForText("Phone’s default · English (UK)")

        compose.onNodeWithText("Pip’s voice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hear a sample").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Reads timer notes and the morning weather. It uses your phone’s own voices, so nothing is sent anywhere.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hearASampleSpeaksTheSampleSentence() {
        val speaker = FakeSpeaker(listOf(british))
        show(speaker)

        compose.onNodeWithText("Hear a sample").performScrollTo().performClick()

        waitFor { speaker.spoken.isNotEmpty() }
        assertEquals(listOf(VOICE_SAMPLE_PHRASE to SpeechPurpose.SAMPLE), speaker.spoken)
    }

    @Test
    fun thePickerOffersThePhonesDefaultThenEachInstalledVoice() {
        show(FakeSpeaker(listOf(british, britishToo, american)))
        waitForText("Phone’s default · English (UK)")

        openThePicker()

        compose.onNodeWithText("Phone’s default").assertIsSelected()
        compose.onNodeWithText("English (UK) · Voice 1").assertIsDisplayed()
        compose.onNodeWithText("English (UK) · Voice 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("English (US)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pickingAVoiceSavesItSwitchesToItPlaysItAndClosesThePicker() {
        val speaker = FakeSpeaker(listOf(british, american))
        show(speaker)
        waitForText("Phone’s default · English (UK)")
        openThePicker()

        compose.onNodeWithText("English (US)").performScrollTo().performClick()

        waitFor { runBlocking { settings.voiceId.first() } == "us" }
        waitFor { speaker.spoken.isNotEmpty() }
        assertEquals("us", speaker.voice)
        assertEquals(listOf(VOICE_PICKED_PHRASE to SpeechPurpose.SAMPLE), speaker.spoken)
        compose.onNodeWithText("Phone’s default").assertDoesNotExist()
        compose.onNodeWithText("English (US)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pickingThePhonesDefaultForgetsTheSavedVoice() {
        runBlocking { settings.setVoiceId("us") }
        val speaker = FakeSpeaker(listOf(british, american))
        show(speaker)
        waitForText("English (US)")
        openThePicker()
        compose.onNode(hasText("English (US)") and isSelectable()).performScrollTo().assertIsSelected()

        compose.onNodeWithText("Phone’s default").performScrollTo().performClick()

        waitFor { runBlocking { settings.voiceId.first() } == null }
        waitFor { speaker.spoken.isNotEmpty() }
        assertNull(speaker.voice)
        waitForText("Phone’s default · English (UK)")
    }

    @Test
    fun withNoVoicesTheRowSaysSoAndThePickerOffersOnlyTheDefault() {
        show(FakeSpeaker())
        waitForText("No voices on this phone")

        openThePicker()

        compose.onNodeWithText("Phone’s default").assertIsSelected()
        compose.onNodeWithText("This phone has no voice for Pip, so Pip stays quiet. The words stay on screen.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("English", substring = true).assertCountEquals(0)
    }

    @Test
    fun readingTheWeatherAloudIsOffUntilTurnedOnAndThenSaved() {
        show(FakeSpeaker())
        val toggle = compose.onNodeWithText("Pip reads it out when you wake")

        toggle.performScrollTo().assertIsOff()
        compose.onNodeWithText("After your wake-up games. Off unless you turn it on.").assertIsDisplayed()
        toggle.performClick()

        waitFor { runBlocking { settings.readWeatherAloud.first() } }
        toggle.assertIsOn()
    }

    @Test
    fun readingTheWeatherAloudIsHiddenWhileWeatherIsOffButTheVoiceStays() {
        runBlocking { settings.setWeatherEnabled(false) }
        show(FakeSpeaker())

        compose.onNodeWithText("Pip reads it out when you wake").assertDoesNotExist()
        compose.onNodeWithText("Hear a sample").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun atDoubleTextSizeTheVoiceRowsStayWhole() {
        show(FakeSpeaker(listOf(british)), fontScale = 2f)
        waitForText("Phone’s default · English (UK)")

        val row = compose.onNodeWithText("Pip’s voice").performScrollTo().assertIsDisplayed().getBoundsInRoot()
        val sample = compose.onNodeWithText("Hear a sample").performScrollTo().assertIsDisplayed().getBoundsInRoot()
        assertTrue(row.bottom - row.top >= 64.dp, "The voice row should grow with the text, was ${row.bottom - row.top}")
        assertTrue(sample.bottom - sample.top >= 44.dp, "Hear a sample is too small to tap, was ${sample.bottom - sample.top}")
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabelWithThePickerShutAndOpen() {
        show(FakeSpeaker(listOf(british, american)))
        waitForText("Phone’s default · English (UK)")
        assertEquals(emptyList(), unlabelled())

        openThePicker()

        assertEquals(emptyList(), unlabelled())
    }

    private fun unlabelled() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
        val config = node.config
        config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
    }.map { it.config.toString() }
}
