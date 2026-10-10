package app.morrow.ui.edit

import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import app.morrow.domain.MissionType.BREATHE
import app.morrow.domain.MissionType.SCAN
import app.morrow.domain.MissionType.SHAKE
import app.morrow.domain.MissionType.SUMS
import app.morrow.domain.MissionType.TYPE
import app.morrow.domain.MissionType.UNSCRAMBLE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditModelTest {

    private fun alarm(hour: Int, minute: Int, id: Long = 1, games: List<GameStep> = DefaultGames) =
        Alarm(id, hour, minute, "Alarm", emptySet(), games, enabled = true)

    private fun games(vararg steps: Pair<MissionType, Int>) = alarm(7, 0, games = steps.map { (type, rounds) -> GameStep(type, rounds) })

    /** The point on the dial for [minuteOfDay]: midnight at the top, going clockwise. */
    private fun pointFor(minuteOfDay: Double): Pair<Float, Float> {
        val angle = minuteOfDay / 1440.0 * 2 * PI
        return sin(angle).toFloat() to -cos(angle).toFloat()
    }

    @Test
    fun dialQuartersAreMidnightSixNoonAndEighteen() {
        assertEquals(0, dialMinute(0f, -1f))
        assertEquals(6 * 60, dialMinute(1f, 0f))
        assertEquals(12 * 60, dialMinute(0f, 1f))
        assertEquals(18 * 60, dialMinute(-1f, 0f))
    }

    @Test
    fun dialSnapsToFiveMinutes() {
        val (x1, y1) = pointFor(7 * 60 + 2.0)
        assertEquals(7 * 60, dialMinute(x1, y1))
        val (x2, y2) = pointFor(7 * 60 + 3.0)
        assertEquals(7 * 60 + 5, dialMinute(x2, y2))
    }

    @Test
    fun dialJustBeforeMidnightWrapsToMidnight() {
        val (x, y) = pointFor(1439.0)
        assertEquals(0, dialMinute(x, y))
    }

    @Test
    fun nudgingWrapsAroundMidnight() {
        assertEquals(0 to 0, alarm(23, 55).nudged(5).let { it.hour to it.minute })
        assertEquals(23 to 55, alarm(0, 0).nudged(-5).let { it.hour to it.minute })
        assertEquals(7 to 5, alarm(7, 0).nudged(5).let { it.hour to it.minute })
    }

    @Test
    fun daypartsFollowTheDesign() {
        assertEquals("Deep night", daypart(4 * 60 + 59))
        assertEquals("Before dawn", daypart(5 * 60))
        assertEquals("Dawn", daypart(6 * 60))
        assertEquals("Morning", daypart(7 * 60))
        assertEquals("Midday", daypart(11 * 60))
        assertEquals("Afternoon", daypart(14 * 60))
        assertEquals("Golden hour", daypart(17 * 60))
        assertEquals("Dusk", daypart(18 * 60 + 30))
        assertEquals("Night", daypart(20 * 60))
    }

    @Test
    fun atNightTheEstimateIsSleep() {
        assertEquals("Sleep now and that’s 7h 20m of rest", editModel(alarm(6, 30), nowMinute = 23 * 60 + 10).untilText)
    }

    @Test
    fun byDayTheEstimateIsTimeFromNow() {
        assertEquals("That’s 3h 05m from now", editModel(alarm(16, 5), nowMinute = 13 * 60).untilText)
        assertEquals("That’s 24h 00m from now", editModel(alarm(13, 0), nowMinute = 13 * 60).untilText)
    }

    @Test
    fun newAndExistingAlarmsDiffer() {
        val fresh = editModel(alarm(7, 0, id = 0), nowMinute = 600)
        assertEquals("New alarm", fresh.title)
        assertFalse(fresh.canDelete)

        val existing = editModel(alarm(7, 0, id = 3), nowMinute = 600)
        assertEquals("Edit alarm", existing.title)
        assertTrue(existing.canDelete)
        assertEquals("07:00", existing.time)
    }

    @Test
    fun gameRowsListTheGamesInPlayOrder() {
        val rows = editModel(games(SHAKE to 3, SUMS to 1, BREATHE to 5, SCAN to 1), nowMinute = 600).games
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.step })
        assertEquals(listOf("Shake", "Sums", "Breathe", "Scan"), rows.map { it.name })
        assertEquals(listOf("30 shakes", "1 sum", "5 slow breaths", "Your kettle code"), rows.map { it.roundsText })
        assertEquals(listOf(true, true, true, false), rows.map { it.counted })
        assertEquals(listOf(true, false, true, false), rows.map { it.canFewer })
        assertEquals(listOf(true, true, false, false), rows.map { it.canMore })
        assertEquals(listOf(false, true, true, true), rows.map { it.canMoveUp })
        assertEquals(listOf(true, true, true, false), rows.map { it.canMoveDown })
        assertTrue(rows.all { it.removable })
    }

    @Test
    fun theOnlyGameCantBeRemoved() {
        val row = editModel(alarm(7, 0), nowMinute = 600).games.single()
        assertFalse(row.removable)
        assertFalse(row.canMoveUp || row.canMoveDown)
        assertEquals("3 sums", row.roundsText)
    }

    @Test
    fun estimateAddsUpEachRound() {
        assertEquals(36 + 12 + 24 + 40, gamesSeconds(games(SUMS to 3, BREATHE to 2, SHAKE to 3, SCAN to 1).games))
        assertEquals(Estimate("About 2 min to finish", tooLong = false), editModel(games(SUMS to 3, BREATHE to 2, SHAKE to 3, SCAN to 1), nowMinute = 600).estimate)
        assertEquals(4 * 20 + 3 * 15, gamesSeconds(games(TYPE to 4, UNSCRAMBLE to 3).games))
        assertEquals("About 1 min to finish", editModel(games(BREATHE to 1), nowMinute = 600).estimate.text)
    }

    @Test
    fun overEightMinutesTheEstimateWarns() {
        assertEquals(Estimate("About 8 min to finish", tooLong = false), estimate(480))
        assertEquals(Estimate("About 8 min. Pip stops singing at 10, so keep it shorter.", tooLong = true), estimate(481))
        assertEquals(Estimate("About 9 min. Pip stops singing at 10, so keep it shorter.", tooLong = true), estimate(540))
    }

    @Test
    fun theAddSheetListsEveryGameAndDimsTheAddedOnes() {
        val model = editModel(games(BREATHE to 3, SCAN to 1), nowMinute = 600)
        assertEquals(MissionType.entries, model.gameChoices.map { it.type })
        assertEquals(listOf(false, false, false, true, false, true), model.gameChoices.map { it.added })
        assertEquals("Quick mental maths. Best if you sleep through anything.", model.gameChoices.first().description)
        assertTrue(model.canAddGame)
        assertFalse(editModel(games(SUMS to 3, TYPE to 2, UNSCRAMBLE to 2, BREATHE to 3, SHAKE to 3, SCAN to 1), nowMinute = 600).canAddGame)
    }

    @Test
    fun roundsStayWithinTheGamesLimits() {
        val draft = games(SUMS to 3, SHAKE to 6)
        assertEquals(4, draft.withRounds(SUMS, 4).games[0].rounds)
        assertEquals(1, draft.withRounds(SUMS, 0).games[0].rounds)
        assertEquals(6, draft.withRounds(SHAKE, 7).games[1].rounds)
    }

    @Test
    fun addingAppendsWithTheUsualRoundsOnce() {
        val draft = alarm(7, 0).withGame(SHAKE)
        assertEquals(listOf(GameStep(SUMS, 3), GameStep(SHAKE, 3)), draft.games)
        assertEquals(draft, draft.withGame(SHAKE))
    }

    @Test
    fun removingKeepsAtLeastOneGame() {
        val draft = games(SUMS to 3, BREATHE to 2)
        assertEquals(listOf(GameStep(BREATHE, 2)), draft.withoutGame(SUMS).games)
        assertEquals(listOf(GameStep(BREATHE, 2)), draft.withoutGame(SUMS).withoutGame(BREATHE).games)
    }

    @Test
    fun movingChangesThePlayOrderAndStopsAtTheEnds() {
        val draft = games(SUMS to 3, BREATHE to 2, SCAN to 1)
        assertEquals(listOf(BREATHE, SUMS, SCAN), draft.withGameMoved(BREATHE, -1).games.map { it.type })
        assertEquals(listOf(BREATHE, SCAN, SUMS), draft.withGameMoved(SUMS, 2).games.map { it.type })
        assertEquals(draft, draft.withGameMoved(SUMS, -1))
        assertEquals(draft, draft.withGameMoved(SCAN, 1))
        assertEquals(draft, draft.withGameMoved(SHAKE, 1))
    }
}
