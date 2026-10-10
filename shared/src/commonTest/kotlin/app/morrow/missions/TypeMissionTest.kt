package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypeMissionTest {

    private fun mission(rounds: Int = 2, difficulty: Difficulty = Difficulty.NORMAL) = TypeMission(rounds, difficulty, Random(3))

    @Test
    fun aPastedSentenceIsIgnored() {
        val mission = TypeMission(rounds = 1, Difficulty.NORMAL, Random(1))

        mission.type(mission.state.value.sentence)

        assertEquals("", mission.state.value.typed)
        assertEquals(0, mission.state.value.finished)
    }

    @Test
    fun theSentenceComesFromTheDifficulty() {
        for (difficulty in Difficulty.entries) {
            assertTrue(mission(difficulty = difficulty).state.value.sentence in sentences(difficulty))
        }
    }

    @Test
    fun startsEmptyWithNothingMatched() {
        val state = mission().state.value
        assertEquals("", state.typed)
        assertEquals(0, state.matched)
        assertNull(state.typoWord)
        assertEquals(0f, state.progress)
        assertFalse(state.done)
    }

    @Test
    fun theCorrectPrefixIgnoresCaseAndRepeatedSpaces() {
        val mission = mission()
        val sentence = mission.state.value.sentence
        val firstWords = sentence.substringBefore(' ')
        val second = sentence.substringAfter(' ').substringBefore(' ')

        mission.typeOut(firstWords.uppercase())
        assertEquals(firstWords.length, mission.state.value.matched)
        assertNull(mission.state.value.typoWord)

        mission.typeOut("  $firstWords    ${second.lowercase()}")
        assertEquals(firstWords.length + 1 + second.length, mission.state.value.matched)
        assertNull(mission.state.value.typoWord)
    }

    @Test
    fun aTypoNamesTheWordToCheckWithoutPunctuation() {
        val mission = mission(difficulty = Difficulty.GENTLE)
        val sentence = mission.state.value.sentence
        val words = sentence.removeSuffix(".").split(' ')

        mission.typeOut(words[0] + " " + "#")
        assertEquals(words[1], mission.state.value.typoWord)
        assertEquals(words[0].length + 1, mission.state.value.matched)

        mission.typeOut("#")
        assertEquals(words[0], mission.state.value.typoWord)

        mission.typeOut(words.dropLast(1).joinToString(" ") + " #")
        assertEquals(words.last(), mission.state.value.typoWord)
    }

    @Test
    fun anExtraLetterAfterAFinishedWordPointsBackAtThatWord() {
        val mission = mission()
        val first = mission.state.value.sentence.substringBefore(' ')
        mission.typeOut(first + "x")
        assertEquals(first, mission.state.value.typoWord)
    }

    @Test
    fun aTypoClearsOnceItIsFixed() {
        val mission = mission()
        val first = mission.state.value.sentence.substringBefore(' ')
        mission.typeOut(first + "x")
        mission.typeOut(first)
        assertNull(mission.state.value.typoWord)
    }

    @Test
    fun progressCountsTheMatchedPrefixOnlyWhileThereIsNoTypo() {
        val mission = mission(rounds = 2)
        val sentence = mission.state.value.sentence
        val first = sentence.substringBefore(' ')

        mission.typeOut(first)
        assertEquals(first.length.toFloat() / sentence.length / 2, mission.state.value.progress)

        mission.typeOut(first + "x")
        assertEquals(0f, mission.state.value.progress)
    }

    @Test
    fun aMatchedSentenceFinishesTheRoundClearsTheFieldAndPicksAnotherSentence() {
        val mission = mission(rounds = 3)
        val first = mission.state.value.sentence

        mission.typeOut(first)

        val state = mission.state.value
        assertEquals(1, state.finished)
        assertEquals("", state.typed)
        assertNotEquals(first, state.sentence)
        assertEquals(1f / 3, state.progress)
        assertFalse(state.done)
    }

    @Test
    fun caseSpacesAndTheFinalFullStopDoNotMatter() {
        for (variant in listOf<(String) -> String>(
            { it.lowercase() },
            { it.uppercase() },
            { it.removeSuffix(".") },
            { it.replace(" ", "   ") },
            { " $it " },
            { it.removeSuffix(".").uppercase() + "  " },
        )) {
            val mission = mission(rounds = 1)
            mission.typeOut(variant(mission.state.value.sentence))
            assertTrue(mission.state.value.done, "variant failed")
        }
    }

    @Test
    fun aMissingWordOrLetterIsNotAMatch() {
        val mission = mission(rounds = 1)
        val sentence = mission.state.value.sentence
        mission.typeOut(sentence.removeSuffix(".").dropLast(1))
        assertFalse(mission.state.value.done)
        mission.typeOut(sentence.removeSuffix(".") + "s")
        assertFalse(mission.state.value.done)
    }

    @Test
    fun everyRoundGetsADifferentSentence() {
        val mission = mission(rounds = 5)
        val seen = mutableListOf<String>()
        repeat(5) {
            seen += mission.state.value.sentence
            mission.typeOut(mission.state.value.sentence)
        }
        assertEquals(5, seen.toSet().size)
        assertTrue(mission.state.value.done)
    }

    @Test
    fun finishingAllRoundsIsDoneAndLaterInputIsIgnored() {
        val mission = mission(rounds = 2)
        repeat(2) { mission.typeOut(mission.state.value.sentence) }
        val done = mission.state.value
        assertTrue(done.done)
        assertEquals(1f, done.progress)

        mission.typeOut("something else")
        assertEquals(done, mission.state.value)
    }
}

/** Types [text] a word at a time, as a person would: the field only ever grows by a word. */
private fun TypeMission.typeOut(text: String) {
    val words = text.split(" ")
    words.indices.forEach { i -> type(words.take(i + 1).joinToString(" ")) }
}
