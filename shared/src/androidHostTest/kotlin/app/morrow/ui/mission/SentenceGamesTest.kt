package app.morrow.ui.mission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.morrow.domain.Difficulty
import app.morrow.missions.TypeMission
import app.morrow.missions.UnscrambleMission
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SentenceGamesTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private fun showType(mission: TypeMission) = compose.setContent {
        MorrowTheme(ThemeKind.NIGHT) {
            val state by mission.state.collectAsState()
            TypeBody(state, mission)
        }
    }

    private fun showUnscramble(mission: UnscrambleMission) = compose.setContent {
        MorrowTheme(ThemeKind.NIGHT) {
            val state by mission.state.collectAsState()
            UnscrambleBody(state, mission)
        }
    }

    @Test
    fun typeShowsTheSentenceAndAnEmptyFieldWithAHint() {
        val mission = TypeMission(1, Difficulty.GENTLE, Random(1))
        showType(mission)

        compose.onNodeWithText("TYPE THIS").assertExists()
        compose.onNodeWithText(mission.state.value.sentence).assertExists()
        compose.onNodeWithText("Your sentence").assertExists()
        compose.onNodeWithContentDescription("Your sentence").assertExists()
        compose.onNodeWithText("Type it exactly. Capitals and the full stop don’t matter.").assertExists()
    }

    @Test
    fun typingTheSentenceFinishesAOneRoundGame() {
        val mission = TypeMission(1, Difficulty.NORMAL, Random(2))
        showType(mission)

        compose.onNodeWithContentDescription("Your sentence").performTextInput(mission.state.value.sentence.lowercase())
        compose.waitForIdle()

        assertTrue(mission.state.value.done)
    }

    @Test
    fun typingPartOfTheSentenceKeepsGoingWithAnEncouragingHint() {
        val mission = TypeMission(1, Difficulty.NORMAL, Random(2))
        showType(mission)

        compose.onNodeWithContentDescription("Your sentence").performTextInput(mission.state.value.sentence.take(5))

        compose.onNodeWithText("Looking good. Keep going.").assertExists()
        assertTrue(!mission.state.value.done)
    }

    @Test
    fun aTypoNamesTheWordToCheck() {
        val mission = TypeMission(1, Difficulty.GENTLE, Random(1))
        showType(mission)
        val first = mission.state.value.sentence.substringBefore(' ').removeSuffix(".")

        compose.onNodeWithContentDescription("Your sentence").performTextInput("${first}z")

        compose.onNodeWithText("Check “$first”. It doesn’t match yet.").assertExists()
    }

    @Test
    fun typeMovesOnToTheNextSentenceInALongerGame() {
        val mission = TypeMission(2, Difficulty.GENTLE, Random(1))
        showType(mission)
        val first = mission.state.value.sentence

        compose.onNodeWithContentDescription("Your sentence").performTextInput(first)
        compose.waitForIdle()

        val next = mission.state.value.sentence
        assertTrue(next != first)
        compose.onNodeWithText(next).assertExists()
        compose.onNodeWithText("Type it exactly. Capitals and the full stop don’t matter.").assertExists()
    }

    @Test
    fun unscrambleStartsWithEveryWordAsAChipAndAHint() {
        val mission = UnscrambleMission(1, Difficulty.GENTLE, Random(1))
        showUnscramble(mission)

        mission.state.value.words.forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithContentDescription("So far: nothing yet").assertExists()
        compose.onNodeWithText("Tap the first word of the sentence.").assertExists()
    }

    @Test
    fun tappingTheWordsInOrderFinishesAOneRoundGame() {
        val mission = UnscrambleMission(1, Difficulty.GENTLE, Random(1))
        showUnscramble(mission)
        val words = mission.state.value.words

        words.forEachIndexed { i, word ->
            compose.onNodeWithText(word).performClick()
            compose.waitForIdle()
            if (i < words.lastIndex) {
                compose.onNodeWithContentDescription("So far: ${words.take(i + 1).joinToString(" ")}").assertExists()
                compose.onNodeWithText("Now the next word.").assertExists()
            }
        }

        assertTrue(mission.state.value.done)
    }

    @Test
    fun aWrongTapPlacesNothingAndHintsAtTheLastWord() {
        val mission = UnscrambleMission(1, Difficulty.GENTLE, Random(1))
        showUnscramble(mission)
        val words = mission.state.value.words

        compose.onNodeWithText(words[0]).performClick()
        compose.onNodeWithText(words[2]).performClick()
        compose.waitForIdle()

        assertEquals(1, mission.state.value.placed)
        assertEquals(1, mission.state.value.wrong)
        compose.onNodeWithContentDescription("So far: ${words[0]}").assertExists()
        compose.onNodeWithText("Not that one yet. What comes after “${words[0]}”?").assertExists()
        compose.onNodeWithText(words[2]).assertExists()
    }

    @Test
    fun everyControlHasALabel() {
        val unscramble = UnscrambleMission(1, Difficulty.GENTLE, Random(1))
        showUnscramble(unscramble)
        assertAllClickablesLabelled()
    }

    @Test
    fun theTypeFieldIsEditableAndLabelled() {
        showType(TypeMission(1, Difficulty.GENTLE, Random(1)))
        val node = compose.onNodeWithContentDescription("Your sentence").fetchSemanticsNode()
        assertNotNull(node.config.getOrNull(SemanticsActions.SetText))
    }

    private fun assertAllClickablesLabelled() {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        nodes.forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
            assertTrue(text.isNotBlank() || description.isNotBlank(), "an unlabelled control")
        }
    }
}
