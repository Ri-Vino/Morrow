package app.morrow.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GamesTest {

    private fun alarm(games: List<GameStep>) = Alarm(1, 7, 0, "Alarm", emptySet(), games, enabled = true)

    @Test
    fun gamesRoundTripThroughText() {
        val games = listOf(GameStep(MissionType.SHAKE, 6), GameStep(MissionType.SUMS, 10), GameStep(MissionType.TYPE, 5), GameStep(MissionType.UNSCRAMBLE, 2), GameStep(MissionType.BREATHE, 1), GameStep(MissionType.SCAN, 1))
        assertEquals("SHAKE:6,SUMS:10,TYPE:5,UNSCRAMBLE:2,BREATHE:1,SCAN:1", encodeGames(games))
        assertEquals(games, decodeGames(encodeGames(games)))
        assertEquals(DefaultGames, decodeGames(encodeGames(DefaultGames)))
    }

    @Test
    fun unreadableStepsAreLeftOutOrKeptWithinLimits() {
        assertEquals(
            listOf(GameStep(MissionType.SUMS, 10), GameStep(MissionType.BREATHE, 3), GameStep(MissionType.SCAN, 1)),
            decodeGames("SUMS:99,JUGGLE:2,BREATHE,SCAN:0"),
        )
        assertEquals(emptyList(), decodeGames(""))
    }

    @Test
    fun eachGameHasItsLimitsAndUsualRounds() {
        assertEquals(listOf(10, 5, 5, 5, 6, 1), MissionType.entries.map { it.rules.maxRounds })
        assertEquals(listOf(3, 2, 2, 3, 3, 1), MissionType.entries.map { it.rules.defaultRounds })
        assertEquals(listOf(12, 20, 15, 6, 8, 40), MissionType.entries.map { it.rules.secondsPerRound })
        assertEquals(GameStep(MissionType.SHAKE, 3), GameStep(MissionType.SHAKE))
        assertFailsWith<IllegalArgumentException> { GameStep(MissionType.SHAKE, 7) }
        assertFailsWith<IllegalArgumentException> { GameStep(MissionType.SUMS, 0) }
        assertFailsWith<IllegalArgumentException> { GameStep(MissionType.SCAN, 2) }
        assertFailsWith<IllegalArgumentException> { GameStep(MissionType.TYPE, 6) }
    }

    @Test
    fun anAlarmPlaysAtLeastOneGameAndEachOnlyOnce() {
        assertFailsWith<IllegalArgumentException> { alarm(emptyList()) }
        assertFailsWith<IllegalArgumentException> { alarm(listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.SUMS, 2))) }
        alarm(listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2)))
    }
}
