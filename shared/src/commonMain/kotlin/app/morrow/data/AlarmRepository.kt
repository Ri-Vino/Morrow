package app.morrow.data

import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek

class AlarmRepository(private val dao: AlarmDao) {

    /** All alarms, sorted by time of day. */
    val alarms: Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

    suspend fun getAll(): List<Alarm> = dao.getAll().map { it.toAlarm() }

    suspend fun get(id: Long): Alarm? = dao.get(id)?.toAlarm()

    /** Inserts when [Alarm.id] is 0, otherwise updates. Returns the alarm's id. */
    suspend fun save(alarm: Alarm): Long {
        val rowId = dao.upsert(alarm.toEntity())
        return if (alarm.id == 0L) rowId else alarm.id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.delete(id)

    /** On a fresh install, saves [FirstAlarm] to start from. Does nothing once there's any alarm. */
    suspend fun seedFirstAlarm() {
        if (dao.getAll().isEmpty()) save(FirstAlarm)
    }

    companion object {
        /** 08:00 on weekdays, off until it's turned on, so nothing rings before the user has chosen to. */
        val FirstAlarm = Alarm(
            id = 0,
            hour = 8,
            minute = 0,
            label = "Alarm",
            days = DayOfWeek.entries.take(5).toSet(),
            games = DefaultGames,
            enabled = false,
        )
    }
}
