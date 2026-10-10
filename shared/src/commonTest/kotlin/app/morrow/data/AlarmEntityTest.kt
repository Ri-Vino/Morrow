package app.morrow.data

import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmEntityTest {

    @Test
    fun anAlarmRoundTripsThroughItsRow() {
        val alarm = Alarm(
            id = 4, hour = 6, minute = 30, label = "Work days", days = setOf(MONDAY, SUNDAY),
            games = listOf(GameStep(MissionType.SUMS, 3), GameStep(MissionType.BREATHE, 2)), enabled = true,
        )
        val row = alarm.toEntity()
        assertEquals("SUMS:3,BREATHE:2", row.games)
        assertEquals(alarm, row.toAlarm())
    }

    @Test
    fun aRowWithNoReadableGamesStillRings() {
        val row = AlarmEntity(1, 7, 0, "Alarm", days = 0, games = "JUGGLE:3", enabled = true)
        assertEquals(DefaultGames, row.toAlarm().games)
    }

    @Test
    fun aRowWithAGameTwiceKeepsTheFirst() {
        val row = AlarmEntity(1, 7, 0, "Alarm", days = 0, games = "SHAKE:2,SUMS:3,SHAKE:5", enabled = true)
        assertEquals(listOf(GameStep(MissionType.SHAKE, 2), GameStep(MissionType.SUMS, 3)), row.toAlarm().games)
    }
}
