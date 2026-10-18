package app.morrow.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnoozeTest {

    private fun alarm(minutes: Int = 5, limit: Int = 3) =
        Alarm(1, 7, 0, "Alarm", emptySet(), DefaultGames, enabled = true, snoozeMinutes = minutes, snoozeLimit = limit)

    @Test
    fun anAlarmSnoozesUpToItsLimit() {
        val alarm = alarm(limit = 2)
        assertTrue(canSnooze(alarm, snoozed = 0))
        assertTrue(canSnooze(alarm, snoozed = 1))
        assertFalse(canSnooze(alarm, snoozed = 2))
    }

    @Test
    fun noLimitMeansItCanAlwaysBeSnoozed() {
        val alarm = alarm(limit = UNLIMITED_SNOOZES)
        assertTrue(listOf(0, 1, 3, 10, 500).all { canSnooze(alarm, it) })
    }

    @Test
    fun aLengthOfZeroStillMeansNoSnoozing() {
        assertFalse(canSnooze(alarm(minutes = 0, limit = UNLIMITED_SNOOZES), snoozed = 0))
        assertFalse(canSnooze(alarm(minutes = 0), snoozed = 0))
    }

    @Test
    fun anyLengthUpToAnHourCanSnooze() {
        listOf(1, 7, 45, MAX_SNOOZE_MINUTES).forEach { assertTrue(canSnooze(alarm(minutes = it), snoozed = 0), "$it min") }
    }

    @Test
    fun theLimitChoicesEndWithUnlimited() {
        assertEquals(listOf(1, 2, 3, 5, 10, UNLIMITED_SNOOZES), SnoozeLimitChoices)
        assertEquals(0, UNLIMITED_SNOOZES)
    }
}
