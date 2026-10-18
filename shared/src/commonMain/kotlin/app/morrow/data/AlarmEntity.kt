package app.morrow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morrow.domain.Alarm
import app.morrow.domain.DEFAULT_SNOOZE_LIMIT
import app.morrow.domain.DEFAULT_SNOOZE_MINUTES
import app.morrow.domain.DefaultGames
import app.morrow.domain.Difficulty
import app.morrow.domain.MAX_SNOOZE_MINUTES
import app.morrow.domain.UNLIMITED_SNOOZES
import app.morrow.domain.decodeGames
import app.morrow.domain.decodeRingSound
import app.morrow.domain.encode
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
    /** As [encode] writes it, or null for the one chosen in Settings. */
    val sound: String? = null,
    /** A [Difficulty]'s name, or null for an alarm saved before each had its own. */
    val difficulty: String? = null,
    @ColumnInfo(name = "snooze_minutes", defaultValue = "5") val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    @ColumnInfo(name = "snooze_limit", defaultValue = "3") val snoozeLimit: Int = DEFAULT_SNOOZE_LIMIT,
    /** Shown on the ring screen under [label], or null for none. */
    val description: String? = null,
    /** Deleted once it has rung and been dealt with. */
    @ColumnInfo(defaultValue = "0") val temporary: Boolean = false,
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
    sound = sound?.encode(),
    difficulty = difficulty?.name,
    snoozeMinutes = snoozeMinutes,
    snoozeLimit = snoozeLimit,
    description = description,
    temporary = temporary,
)

internal fun AlarmEntity.toAlarm() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    // A temporary alarm never repeats, so days on one would be a mistake: they're dropped rather than refused.
    days = if (temporary) emptySet() else DayOfWeek.entries.filterTo(mutableSetOf()) { days and (1 shl (it.isoDayNumber - 1)) != 0 },
    games = decodeGames(games).distinctBy { it.type }.ifEmpty { DefaultGames },
    enabled = enabled,
    timeZone = timeZone,
    zoneLabel = zoneLabel,
    // A sound this version doesn't know rings with the default.
    sound = decodeRingSound(sound),
    difficulty = Difficulty.entries.firstOrNull { it.name == difficulty },
    snoozeMinutes = snoozeMinutes.coerceIn(0, MAX_SNOOZE_MINUTES),
    snoozeLimit = snoozeLimit.coerceAtLeast(UNLIMITED_SNOOZES),
    description = description?.takeIf(String::isNotBlank),
    temporary = temporary,
)
