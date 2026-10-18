package app.morrow.ui.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.FakeSpeaker
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import app.morrow.ui.settings.SettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    // Gradle runs a module's tests from the module's own folder.
    private val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/make_sounds.py").exists() }
    private val resources = File(repo, "shared/src/commonMain/composeResources")

    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }) {
                MorrowTheme(ThemeKind.MORNING) {
                    AboutRoute(version = "0.1.0-beta.1", onBack = { backs++ }, read = { File(resources, it).readText() })
                }
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun settingsOpensLegalAndAbout() {
        var opens = 0
        val permissions = object : PermissionGate {
            override val asked = AndroidPermissions
            override val missing: StateFlow<List<AlarmPermission>> = MutableStateFlow(emptyList())
            override fun refresh() = Unit
            override fun request(permission: AlarmPermission) = Unit
        }
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                SettingsRoute(
                    settings = MorrowSettings(InMemoryPreferences()),
                    permissions = permissions,
                    cities = CityRepository(FakeSavedCityDao(), CityCatalog { "" }) { "Europe/London" },
                    here = MutableStateFlow(null),
                    phoneCountry = "GB",
                    speaker = FakeSpeaker(),
                    developerTools = null,
                    onPickHomeCity = {},
                    onWeatherChanged = {},
                    onBack = {},
                    onAbout = { opens++ },
                )
            }
        }
        waitForText("Legal & about")
        compose.onNodeWithText("Legal & about").performScrollTo().performClick()
        assertEquals(1, opens)
    }

    @Test
    fun namesTheOwnerAndVersionAndOpensTheirGitHub() {
        show()
        compose.onNodeWithText("Version 0.1.0-beta.1").assertExists()
        compose.onNodeWithText("This is a beta", substring = true).assertExists()
        compose.onNodeWithText("github.com/Ri-Vino").performScrollTo().assertExists()

        compose.onNodeWithText("Rivino").performScrollTo().performClick()
        compose.onNodeWithText("Report a problem").performScrollTo().performClick()
        assertEquals(listOf(OWNER_URL, ISSUES_URL), opened)
    }

    @Test
    fun thePrivacyPolicyAndTermsReadFromTheAppAndGoBack() {
        show()
        compose.onNodeWithText("Privacy policy").performScrollTo().performClick()
        waitForText("What leaves your phone: weather")
        compose.onNodeWithText("rounded to 2 decimal places", substring = true).assertExists()
        compose.onNodeWithText("Legal & about").assertDoesNotExist()

        compose.onNodeWithContentDescription("Back to legal & about").performClick()
        compose.onNodeWithText("Terms of service").performScrollTo().performClick()
        waitForText("Don't rely on Morrow alone")

        compose.onNodeWithContentDescription("Back to legal & about").performClick()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun eachLicenceOpensItsText() {
        show()
        compose.onNodeWithText("Open-source licences").performScrollTo().performClick()
        compose.onNodeWithText("Fraunces").performScrollTo().performClick()
        waitForText("SIL OPEN FONT LICENSE")

        compose.onNodeWithContentDescription("Back to licences").performClick()
        compose.onNodeWithText("City data").performScrollTo().performClick()
        compose.onNodeWithText("City names, positions and populations", substring = true).assertExists()

        compose.onNodeWithContentDescription("Back to licences").performClick()
        compose.onNodeWithText("Morrow").performScrollTo().performClick()
        waitForText("Apache License")
        assertEquals(0, backs)
    }

    @Test
    fun everyBundledTextIsThere() {
        val files = listOf(PRIVACY_FILE, TERMS_FILE) + Licences.mapNotNull { it.file }
        assertEquals(emptyList(), files.filterNot { File(resources, it).isFile })
        // The licence the app shows for itself is the one at the root of the repository.
        assertEquals(File(repo, "LICENSE").readText(), File(resources, "files/licenses/apache-2.0.txt").readText())
        assertTrue(Licences.all { it.file != null || it.text != null })
    }
}
