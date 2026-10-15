package app.morrow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.WakeSound
import app.morrow.domain.decodeGames
import app.morrow.domain.encodeGames
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
    /** In play order, as [encodeGames] writes them, for example "SUMS:3,BREATHE:2". */
    val games: String,
    val enabled: Boolean,
    /** An IANA zone id, or null to follow the phone's clock. */
    @ColumnInfo(name = "time_zone") val timeZone: String? = null,
    @ColumnInfo(name = "zone_label") val zoneLabel: String? = null,
    /** A [WakeSound]'s name, or null for the one chosen in Settings. */
    val sound: String? = null,
)

internal fun Alarm.toEntity() = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    days = days.fold(0) { mask, day -> mask or (1 shl (day.isoDayNumber - 1)) },
    games = encodeGames(games),
    enabled = enabled,
    timeZone = timeZone,
    zoneLabel = zoneLabel,
    sound = sound?.name,
)

internal fun AlarmEntity.toAlarm() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    days = DayOfWeek.entries.filterTo(mutableSetOf()) { days and (1 shl (it.isoDayNumber - 1)) != 0 },
    games = decodeGames(games).distinctBy { it.type }.ifEmpty { DefaultGames },
    enabled = enabled,
    timeZone = timeZone,
    zoneLabel = zoneLabel,
    // A sound this version doesn't know rings with the default.
    sound = WakeSound.entries.firstOrNull { it.name == sound },
)
