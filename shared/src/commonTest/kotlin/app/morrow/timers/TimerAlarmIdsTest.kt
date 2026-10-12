package app.morrow.timers

import app.morrow.alarms.FIXED_DATE_SLOTS
import app.morrow.alarms.alarmIdOf
import app.morrow.alarms.fixedDateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimerAlarmIdsTest {

    private val alarmIds = listOf(1L, 2L, 7L, 123_456L, 0xFFFF_FFFFL)
    private val timerIds = listOf(1L, 2L, 7L, 123_456L, 0xFFFF_FFFFL, 1L shl 40)

    @Test
    fun timersNeverShareAnIdWithAnAlarmOrADate() {
        val alarmSide = alarmIds.flatMap { id -> listOf(id) + (1..FIXED_DATE_SLOTS).map { fixedDateId(id, it) } }.toSet()
        val timerSide = timerIds.map(::timerAlarmKitId).toSet()

        assertEquals(emptySet(), alarmSide intersect timerSide)
        assertTrue(timerSide.all(::isTimerAlarmKitId))
        assertTrue(alarmSide.none(::isTimerAlarmKitId))
    }

    @Test
    fun aTimersIdComesBack() {
        timerIds.forEach { assertEquals(it, timerIdOf(timerAlarmKitId(it))) }
    }

    @Test
    fun theyFitInAlarmKitScheduler48Bits() {
        assertTrue(timerAlarmKitId((1L shl 47) - 1) < 1L shl 48)
        assertFailsWith<IllegalArgumentException> { timerAlarmKitId(1L shl 47) }
        assertFailsWith<IllegalArgumentException> { timerAlarmKitId(0) }
    }

    @Test
    fun anAlarmsIdsStayWhatTheyWere() {
        assertFalse(isTimerAlarmKitId(fixedDateId(42, FIXED_DATE_SLOTS)))
        assertEquals(42L, alarmIdOf(fixedDateId(42, 3)))
    }
}
