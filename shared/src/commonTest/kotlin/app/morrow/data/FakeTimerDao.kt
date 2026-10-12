package app.morrow.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

class FakeTimerDao : TimerDao {
    val rows = MutableStateFlow<Map<Long, TimerEntity>>(emptyMap())
    private var nextId = 1L

    /** How long [getAll] takes, in virtual time, as a database read does in real time. */
    var readTime: Duration = Duration.ZERO

    override fun observeAll(): Flow<List<TimerEntity>> = rows.map(::ordered)

    override suspend fun getAll(): List<TimerEntity> {
        if (readTime.isPositive()) delay(readTime)
        return ordered(rows.value)
    }

    override suspend fun get(id: Long): TimerEntity? = rows.value[id]

    // Mirrors Room's @Upsert: the new row id on insert, -1 on update. Ids are never reused, like AUTOINCREMENT.
    override suspend fun upsert(timer: TimerEntity): Long {
        if (timer.id != 0L && timer.id in rows.value) {
            rows.value += timer.id to timer
            return -1
        }
        val id = if (timer.id != 0L) timer.id else nextId++
        rows.value += id to timer.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        rows.value -= id
    }

    private fun ordered(rows: Map<Long, TimerEntity>) = rows.values.sortedWith(compareBy(TimerEntity::createdAt, TimerEntity::id))
}
