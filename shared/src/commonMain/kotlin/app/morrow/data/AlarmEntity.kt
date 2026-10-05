package app.morrow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    /** Bit 0 is Monday, bit 6 is Sunday. */
    val days: Int,
    val mission: MissionType,
    val enabled: Boolean,
)

internal fun Alarm.toEntity() = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    days = days.fold(0) { mask, day -> mask or (1 shl (day.isoDayNumber - 1)) },
    mission = mission,
    enabled = enabled,
)

internal fun AlarmEntity.toAlarm() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    days = DayOfWeek.entries.filterTo(mutableSetOf()) { days and (1 shl (it.isoDayNumber - 1)) != 0 },
    mission = mission,
    enabled = enabled,
)
