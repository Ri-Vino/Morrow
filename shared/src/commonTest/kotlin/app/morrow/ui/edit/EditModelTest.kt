package app.morrow.ui.edit

import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditModelTest {

    private fun alarm(hour: Int, minute: Int, id: Long = 1, mission: MissionType = MissionType.SUMS) =
        Alarm(id, hour, minute, "Alarm", emptySet(), mission, enabled = true)

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
    fun missionDescriptionsUseTheMissionSize() {
        assertEquals(
            "Solve 3 quick sums. Best if you sleep through anything.",
            editModel(alarm(7, 0), nowMinute = 600).missionDescription,
        )
        assertEquals(
            "Shake your phone 45 times to get the blood moving.",
            editModel(alarm(7, 0, mission = MissionType.SHAKE), nowMinute = 600, shakeGoal = 45).missionDescription,
        )
    }
}
