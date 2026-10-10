package app.morrow.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
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
    fun theStepperChangesRounds() {
        show(alarm())

        compose.onNodeWithContentDescription("More sums rounds").performScrollTo().performClick()
        compose.onNodeWithText("4 sums").assertExists()
        repeat(4) { compose.onNodeWithContentDescription("Fewer sums rounds").performClick() }

        assertEquals(listOf(GameStep(MissionType.SUMS, 1)), draft.games)
        compose.onNodeWithText("1 sum").assertExists()
    }

    @Test
    fun theAddSheetAddsAGame() {
        show(alarm())

        compose.onNodeWithText("Add a game").performScrollTo().performClick()
        compose.onNodeWithText("Add a wake-up game").assertExists()
        // Sums is already on the alarm, so it can't be picked again.
        compose.onNode(hasText("Sums") and hasText("Added")).assertExists().assertHasNoClickAction()
        compose.onNode(hasText("Breathe") and hasClickAction()).performScrollTo().performClick()

        assertEquals(listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 3)), draft.games)
        compose.onNodeWithText("Add a wake-up game").assertDoesNotExist()
        compose.onNodeWithText("3 slow breaths").assertExists()
    }

    @Test
    fun theAddSheetListsSixGamesAndCanAddType() {
        show(alarm())

        compose.onNodeWithText("Add a game").performScrollTo().performClick()
        listOf("Sums", "Type", "Unscramble", "Breathe", "Shake", "Scan").forEach {
            compose.onNode(hasText(it) and (hasClickAction() or hasText("Added"))).assertExists()
        }
        compose.onNode(hasText("Type") and hasClickAction()).performScrollTo().performClick()

        assertEquals(listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.TYPE, 2)), draft.games)
        compose.onNodeWithText("2 sentences").assertExists()
    }

    @Test
    fun aGameCanBeRemovedUnlessItsTheLastOne() {
        show(alarm(games = listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.SHAKE, 2))))

        compose.onNodeWithContentDescription("Remove Sums").performScrollTo().performClick()

        assertEquals(listOf(GameStep(MissionType.SHAKE, 2)), draft.games)
        compose.onNodeWithContentDescription("Remove Shake").assertDoesNotExist()
    }

    @Test
    fun talkBackCanMoveAGame() {
        show(alarm(games = listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2), GameStep(MissionType.SCAN, 1))))

        moveAction("Breathe", "Move up")
        assertEquals(listOf(MissionType.BREATHE, MissionType.SUMS, MissionType.SCAN), draft.games.map { it.type })
        moveAction("Breathe", "Move down")
        moveAction("Breathe", "Move down")
        assertEquals(listOf(MissionType.SUMS, MissionType.SCAN, MissionType.BREATHE), draft.games.map { it.type })

        assertEquals(listOf("Move up"), actionsOf("Breathe"))
        assertEquals(listOf("Move down"), actionsOf("Sums"))
    }

    @Test
    fun theEstimateFollowsTheRounds() {
        show(alarm(games = listOf(GameStep(MissionType.SUMS, 5))))
        compose.onNodeWithText("About 1 min to finish").performScrollTo()

        compose.onNodeWithContentDescription("More sums rounds").performClick()

        compose.onNodeWithText("About 2 min to finish").assertExists()
        compose.onNodeWithContentDescription("Remove Sums").assertDoesNotExist()
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
        show(alarm(id = 4, games = listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.SCAN, 1))))
        assertAllLabelled()

        compose.onNodeWithText("Add a game").performScrollTo().performClick()
        assertAllLabelled()
    }

    private fun assertAllLabelled() {
        val unlabelled = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }

    /** The row for [game], which carries its TalkBack actions. */
    private fun gameRow(game: String) = compose.onNode(hasText(game) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))

    private fun actionsOf(game: String) =
        gameRow(game).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun moveAction(game: String, label: String) {
        val action = gameRow(game).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }

    private fun alarm(
        id: Long = 1,
        hour: Int = 7,
        minute: Int = 0,
        days: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet(),
        games: List<GameStep> = listOf(GameStep(MissionType.SUMS, 3)),
    ) = Alarm(id, hour, minute, label = "Alarm", days = days, games = games, enabled = true)
}
