package app.morrow.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val london = TimeZone.of("Europe/London")

class SleepMathTest {

    private fun alarm(hour: Int, minute: Int, enabled: Boolean = true) =
        Alarm(id = 1, hour = hour, minute = minute, label = "Work days", days = emptySet(), games = DefaultGames, enabled = enabled)

    @Test
    fun timeUntilTheNextAlarm() {
        val now = LocalDateTime.parse("2026-10-05T23:10").toInstant(london)
        assertEquals(7.hours + 20.minutes, SleepMath.untilNextAlarm(listOf(alarm(6, 30)), now, london))
    }

    @Test
    fun noTimeUntilNextAlarmWhenAllAreOff() {
        val now = LocalDateTime.parse("2026-10-05T23:10").toInstant(london)
        assertNull(SleepMath.untilNextAlarm(listOf(alarm(6, 30, enabled = false)), now, london))
    }

    @Test
    fun countsOnlyFullNinetyMinuteCycles() {
        assertEquals(0, SleepMath.fullCycles(89.minutes))
        assertEquals(1, SleepMath.fullCycles(90.minutes))
        assertEquals(4, SleepMath.fullCycles(7.hours + 20.minutes))
        assertEquals(5, SleepMath.fullCycles(7.hours + 30.minutes))
        assertEquals(0, SleepMath.fullCycles(Duration.ZERO))
    }
}
