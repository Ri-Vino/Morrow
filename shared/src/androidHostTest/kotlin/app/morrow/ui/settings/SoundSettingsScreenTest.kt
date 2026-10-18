package app.morrow.ui.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.domain.WakeSound
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AlarmSound
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.FakeSpeaker
import app.morrow.platform.PermissionGate
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
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
class SoundSettingsScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val settings = MorrowSettings(InMemoryPreferences())
    private val cities = CityRepository(FakeSavedCityDao(), CityCatalog { "" }) { "Europe/London" }
    private val permissions = object : PermissionGate {
        override val asked = AndroidPermissions
        override val missing: StateFlow<List<AlarmPermission>> = MutableStateFlow(emptyList())
        override fun refresh() = Unit
        override fun request(permission: AlarmPermission) = Unit
    }
    private val played = mutableListOf<WakeSound>()
    private var stopped = 0
    private val sound = object : AlarmSound {
        override fun play(sound: WakeSound, ramp: Duration) {
            played += sound
        }

        override fun stop() {
            stopped++
        }
    }

    private fun show() {
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                SettingsRoute(
                    settings = settings,
                    permissions = permissions,
                    cities = cities,
                    here = MutableStateFlow(null),
                    phoneCountry = "GB",
                    speaker = FakeSpeaker(),
                    developerTools = null,
                    onPickHomeCity = {},
                    onWeatherChanged = {},
                    onBack = {},
                    alarmSound = sound,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("DEFAULT ALARM SOUND").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theDefaultSoundIsOneRowThatOpensTheSoundsByKind() {
        show()
        // Only the chosen sound shows until the row is tapped.
        compose.onNodeWithText("Ocean waves").assertDoesNotExist()

        compose.onNodeWithText("Birdsong").performScrollTo().performClick()
        compose.onNodeWithText("NATURE").performScrollTo()
        compose.onNodeWithText("Ocean waves").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { settings.alarmSetup.first().sound } == WakeSound.OCEAN_WAVES }
        compose.onNode(hasText("Ocean waves") and isSelectable()).assertIsSelected()
        // Picking a sound is silent; its speaker plays it.
        assertEquals(emptyList(), played)
        compose.onNodeWithContentDescription("Hear Birdsong").performScrollTo().performClick()
        assertEquals(listOf(WakeSound.BIRDSONG), played)
        assertEquals(WakeSound.OCEAN_WAVES, runBlocking { settings.alarmSetup.first().sound })

        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("NATURE").assertDoesNotExist()
        compose.onNodeWithText("Ocean waves").assertExists()
    }
}
