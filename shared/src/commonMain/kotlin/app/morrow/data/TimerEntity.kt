package app.morrow.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.morrow.timers.Timer
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A timer, running while [endsAt] is set and paused while [remainingMs] is. Times are epoch milliseconds. */
@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long?,
    @ColumnInfo(name = "remaining_ms") val remainingMs: Long?,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY created_at, id")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers ORDER BY created_at, id")
    suspend fun getAll(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun get(id: Long): TimerEntity?

    /** Returns the new row id on insert and -1 on update. */
    @Upsert
    suspend fun upsert(timer: TimerEntity): Long

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun delete(id: Long)
}

internal fun Timer.toEntity() = TimerEntity(
    id = id,
    durationMs = duration.inWholeMilliseconds,
    endsAt = endsAt?.toEpochMilliseconds(),
    remainingMs = remaining?.inWholeMilliseconds,
    note = note,
    createdAt = createdAt.toEpochMilliseconds(),
)

/** A row with neither or both of its end and what's left is read as running, so it still rings rather than vanishing. */
internal fun TimerEntity.toTimer(): Timer {
    val running = endsAt != null || remainingMs == null
    return Timer(
        id = id,
        duration = durationMs.milliseconds,
        endsAt = if (running) Instant.fromEpochMilliseconds(endsAt ?: createdAt + durationMs) else null,
        remaining = if (running) null else remainingMs.milliseconds,
        note = note,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )
}
