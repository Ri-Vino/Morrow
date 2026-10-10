package app.morrow.missions

import app.morrow.domain.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentencesTest {

    private val lengths = mapOf(
        Difficulty.GENTLE to 4..6,
        Difficulty.NORMAL to 6..9,
        Difficulty.TOUGH to 9..12,
    )

    @Test
    fun everyDifficultyHasAboutFortySentencesOfTheRightLength() {
        for (difficulty in Difficulty.entries) {
            val list = sentences(difficulty)
            assertTrue(list.size in 38..45, "${difficulty.name} has ${list.size}")
            for (sentence in list) {
                val count = sentence.split(' ').size
                assertTrue(count in lengths.getValue(difficulty), "$sentence has $count words")
            }
        }
    }

    @Test
    fun noSentenceIsListedTwice() {
        val all = Difficulty.entries.flatMap { sentences(it) }
        assertEquals(all.size, all.map { it.lowercase() }.toSet().size)
    }

    @Test
    fun sentencesAreTypableAndEndWithAFullStop() {
        for (sentence in Difficulty.entries.flatMap { sentences(it) }) {
            assertTrue(sentence.endsWith("."), sentence)
            assertTrue(sentence.dropLast(1).all { it.isLetter() || it == ' ' }, "$sentence has punctuation inside")
            assertTrue(sentence.first().isUpperCase(), sentence)
            assertTrue("  " !in sentence, sentence)
        }
    }

    @Test
    fun scrambleSentencesLoseTheFullStopAndNeverRepeatAWord() {
        for (difficulty in Difficulty.entries) {
            assertEquals(sentences(difficulty).map { it.dropLast(1) }, scrambleSentences(difficulty))
            for (sentence in scrambleSentences(difficulty)) {
                val words = wordsOf(sentence).map { it.lowercase() }
                assertEquals(words.size, words.toSet().size, "$sentence repeats a word")
            }
        }
    }
}
