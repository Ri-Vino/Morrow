package app.morrow.data

import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.GameStep
import app.morrow.domain.MissionType
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun aDescriptionRoundTripsThroughItsRow() {
        val alarm = Alarm(
            id = 3, hour = 6, minute = 0, label = "Gym", days = setOf(MONDAY), games = DefaultGames, enabled = true,
            description = "Gym bag is by the door",
        )
        val row = alarm.toEntity()
        assertEquals("Gym bag is by the door", row.description)
        assertEquals(alarm, row.toAlarm())
    }

    @Test
    fun aBlankDescriptionReadsAsNone() {
        val row = AlarmEntity(1, 7, 0, "Alarm", days = 0, games = "SUMS:3", enabled = true, description = "  ")
        assertNull(row.toAlarm().description)
        assertNull(AlarmEntity(1, 7, 0, "Alarm", days = 0, games = "SUMS:3", enabled = true).toAlarm().description)
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

    @Test
    fun aZonedAlarmKeepsItsZoneAndCity() {
        val alarm = Alarm(
            id = 2, hour = 9, minute = 0, label = "Stand-up", days = setOf(MONDAY), games = DefaultGames, enabled = true,
            timeZone = "America/New_York", zoneLabel = "New York",
        )
        val row = alarm.toEntity()
        assertEquals("America/New_York", row.timeZone)
        assertEquals("New York", row.zoneLabel)
        assertEquals(alarm, row.toAlarm())
    }
}
