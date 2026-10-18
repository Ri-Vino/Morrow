package app.morrow.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.morrow.domain.Alarm
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.RingSound
import app.morrow.domain.WakeSound
import app.morrow.places.City
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val london = TimeZone.of("Europe/London")

/** A Monday at 22:00 in London. */
private val now = LocalDateTime.parse("2026-10-05T22:00").toInstant(london)

private fun city(id: Long, name: String, zone: String, country: String) = City(id, name, null, country, 0.0, 0.0, zone, 1000)

private val newYork = city(5128581, "New York", "America/New_York", "United States")
private val saved = listOf(city(2643743, "London", "Europe/London", "United Kingdom"), newYork)
private val catalog = listOf(newYork, city(1850147, "Tokyo", "Asia/Tokyo", "Japan"))

@RunWith(RobolectricTestRunner::class)
class EditScreenTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private var draft by mutableStateOf(alarm())
    private var savedAlarm = false
    private var deleted = false
    private var leftEdit = 0

    private fun show(alarm: Alarm, unsaved: Boolean = false) {
        draft = alarm
        compose.setContent {
            MorrowTheme(ThemeKind.MORNING) {
                EditScreen(
                    draft = draft,
                    model = editModel(draft, now, london),
                    unsaved = unsaved,
                    zones = ZoneSheetData(
                        choices = zoneChoices(draft, saved, now, london, phoneCity = "London"),
                        find = { query -> searchChoices(catalog.filter { it.name.startsWith(query, ignoreCase = true) }, now, london) },
                    ),
                    actions = EditActions(
                        onChange = { draft = it },
                        onBack = { leftEdit++ },
                        onDelete = { deleted = true },
                        onPreview = {},
                        onSave = { savedAlarm = true },
                    ),
                )
            }
        }
    }

    @Test
    fun theSoundSheetListsTheSoundsByKindAndPicksOne() {
        show(alarm(hour = 7, minute = 0))

        compose.onNodeWithText("Default, as chosen in Settings").performScrollTo().performClick()
        compose.onNodeWithText("Alarm sound").assertExists()
        compose.onNodeWithText("NATURE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ocean waves").performScrollTo().performClick()

        assertEquals(RingSound.Bundled(WakeSound.OCEAN_WAVES), draft.sound)
    }

    @Test
    fun tappingTheTimeLetsItBeTyped() {
        show(alarm(hour = 7, minute = 0))

        compose.onNodeWithText("07:00").performClick()
        listOf("6", "4", "5").forEach { key(it) }
        compose.onNodeWithText("Set time").performClick()

        assertEquals(6 to 45, draft.hour to draft.minute)
        compose.onNodeWithText("06:45").assertExists()
    }

    @Test
    fun theMinutesCanBeTypedOnTheirOwn() {
        show(alarm(hour = 7, minute = 0))

        compose.onNodeWithText("07:00").performClick()
        compose.onNodeWithContentDescription("Minutes, 00").performClick()
        listOf("3", "0").forEach { key(it) }
        compose.onNodeWithText("Set time").performClick()

        assertEquals(7 to 30, draft.hour to draft.minute)
    }

    private fun key(digit: String) = compose.onNode(hasText(digit) and hasClickAction()).performClick()

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

        assertTrue(savedAlarm)
    }

    @Test
    fun pickingNewYorkRingsByItsClock() {
        show(alarm(hour = 9, minute = 0))
        compose.onNodeWithText("Rings by the clock wherever you are").assertExists()

        compose.onNode(hasText("Phone’s time") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Ring by the time in…").assertExists()
        compose.onNode(zoneChoice("Phone’s time")).assertIsSelected()
        compose.onNode(zoneChoice("New York time")).assertIsNotSelected().performClick()

        assertEquals("America/New_York" to "New York", draft.timeZone to draft.zoneLabel)
        compose.onNodeWithText("Ring by the time in…").assertDoesNotExist()
        compose.onNodeWithText("New York time").assertExists()
        compose.onNodeWithText("Follows New York’s clock, summer time included").assertExists()
        compose.onNodeWithText("NEW YORK · MORNING", substring = true).assertExists()
        compose.onNodeWithText("Rings at 14:00 your time · Sleep now and that’s 16h 00m of rest").assertExists()
        compose.onNodeWithText("09:00", substring = true).assertExists()
    }

    @Test
    fun goingBackToThePhonesTimeForgetsTheCity() {
        show(alarm(hour = 9, minute = 0).copy(timeZone = "America/New_York", zoneLabel = "New York"))

        compose.onNode(hasText("New York time") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(zoneChoice("New York time")).assertIsSelected()
        compose.onNode(zoneChoice("Phone’s time")).performClick()

        assertEquals(null to null, draft.timeZone to draft.zoneLabel)
        compose.onNodeWithText("Rings by the clock wherever you are").assertExists()
        compose.onNodeWithText("Sleep now and that’s 11h 00m of rest").assertExists()
    }

    @Test
    fun anotherCityCanBeFoundBySearch() {
        show(alarm(hour = 9, minute = 0))

        compose.onNode(hasText("Phone’s time") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Find another city").performScrollTo().performClick()
        compose.onNodeWithContentDescription("City name").performTextInput("tok")
        compose.onNode(hasText("Tokyo") and hasClickAction()).performClick()

        assertEquals("Asia/Tokyo" to "Tokyo", draft.timeZone to draft.zoneLabel)
        compose.onNodeWithText("Tokyo time").assertExists()
    }

    @Test
    fun aSearchWithNoCityTakesYouNowhere() {
        show(alarm())

        compose.onNode(hasText("Phone’s time") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Find another city").performScrollTo().performClick()
        compose.onNodeWithContentDescription("City name").performTextInput("zzz")

        compose.onNodeWithText("No city by that name").assertExists()
        compose.onNodeWithContentDescription("Back to time zones").performClick()
        compose.onNodeWithText("Ring by the time in…").assertExists()
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        show(alarm(id = 4, games = listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.SCAN, 1))))
        assertAllLabelled()

        compose.onNodeWithText("Add a game").performScrollTo().performClick()
        assertAllLabelled()
    }

    @Test
    fun everyTimeZoneControlHasALabel() {
        show(alarm())

        compose.onNode(hasText("Phone’s time") and hasClickAction()).performScrollTo().performClick()
        assertAllLabelled()

        compose.onNodeWithText("Find another city").performScrollTo().performClick()
        compose.onNodeWithContentDescription("City name").performTextInput("new")
        compose.onNode(hasText("New York") and hasClickAction()).assertExists()
        assertAllLabelled()
    }

    /** A radio button in the time zone sheet. */
    private fun zoneChoice(name: String) = hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

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

    @Test
    fun backWithNothingChangedLeavesAtOnce() {
        show(alarm(hour = 7, minute = 0))

        compose.onNodeWithContentDescription("Back to alarms").performClick()

        assertEquals(1, leftEdit)
    }

    @Test
    fun backWithChangesAsksFirst() {
        show(alarm(hour = 7, minute = 0), unsaved = true)

        compose.onNodeWithContentDescription("Back to alarms").performClick()

        assertEquals(0, leftEdit)
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, leftEdit)

        compose.onNodeWithContentDescription("Back to alarms").performClick()
        compose.onNodeWithText("Discard changes").performScrollTo().performClick()
        assertEquals(1, leftEdit)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun theSevenDaysFitOnANarrowPhone() {
        show(alarm(hour = 7, minute = 0))

        val root = compose.onRoot().getBoundsInRoot()
        val sunday = compose.onNodeWithContentDescription("Sunday").performScrollTo().getBoundsInRoot()
        assertTrue(sunday.right <= root.right, "Sunday ends at ${sunday.right}, past the screen's ${root.right}")
        assertTrue(sunday.right - sunday.left >= 36.dp, "Sunday is only ${sunday.right - sunday.left} wide")
    }
}
