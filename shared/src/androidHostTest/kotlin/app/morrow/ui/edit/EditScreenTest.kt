package app.morrow.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import kotlinx.datetime.DayOfWeek
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class EditScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private var draft by mutableStateOf(alarm())
    private var saved = false
    private var deleted = false

    private fun show(alarm: Alarm) {
        draft = alarm
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                EditScreen(
                    draft = draft,
                    model = editModel(draft, nowMinute = 22 * 60),
                    actions = EditActions(
                        onChange = { draft = it },
                        onBack = {},
                        onDelete = { deleted = true },
                        onPreview = {},
                        onSave = { saved = true },
                    ),
                )
            }
        }
    }

    @Test
    fun plusAndMinusMoveTheTimeByFiveMinutes() {
        show(alarm(hour = 7, minute = 0))

        compose.onNodeWithContentDescription("Five minutes later").performClick()
        compose.onNodeWithText("07:05").assertExists()

        compose.onNodeWithContentDescription("Five minutes earlier").performClick()
        compose.onNodeWithContentDescription("Five minutes earlier").performClick()
        compose.onNodeWithText("06:55").assertExists()
        assertEquals(6 to 55, draft.hour to draft.minute)
    }

    @Test
    fun earlierThanMidnightWrapsToTheNightBefore() {
        show(alarm(hour = 0, minute = 0))

        compose.onNodeWithContentDescription("Five minutes earlier").performClick()

        compose.onNodeWithText("23:55").assertExists()
    }

    @Test
    fun dayChipsTurnDaysOnAndOff() {
        show(alarm(days = DayOfWeek.entries.take(5).toSet()))

        compose.onNodeWithContentDescription("Saturday").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Monday").performClick().assertIsOff()

        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), draft.days)
    }

    @Test
    fun pickingAMissionSelectsAndDescribesIt() {
        show(alarm())

        compose.onNode(hasText("Breathe") and isSelectable()).performScrollTo().performClick().assertIsSelected()

        assertEquals(MissionType.BREATHE, draft.mission)
        compose.onNodeWithText("Hold for three slow breaths. The gentlest way up.").assertExists()
    }

    @Test
    fun aNewAlarmHasNothingToDelete() {
        show(alarm(id = 0))

        compose.onNodeWithContentDescription("Delete alarm").assertDoesNotExist()
    }

    @Test
    fun aSavedAlarmCanBeDeleted() {
        show(alarm(id = 4))

        compose.onNodeWithContentDescription("Delete alarm").performClick()

        assertTrue(deleted)
    }

    @Test
    fun saveSavesTheDraft() {
        show(alarm())

        compose.onNodeWithText("Save alarm").performClick()

        assertTrue(saved)
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show(alarm(id = 4))

        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }

    private fun alarm(
        id: Long = 1,
        hour: Int = 7,
        minute: Int = 0,
        days: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet(),
    ) = Alarm(id, hour, minute, label = "Alarm", days = days, mission = MissionType.SUMS, enabled = true)
}
