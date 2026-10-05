package app.morrow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAlarmDao : AlarmDao {
    private val rows = MutableStateFlow<Map<Long, AlarmEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<AlarmEntity>> =
        rows.map { it.values.sortedWith(compareBy(AlarmEntity::hour, AlarmEntity::minute)) }

    override suspend fun getAll(): List<AlarmEntity> = rows.value.values.toList()

    override suspend fun get(id: Long): AlarmEntity? = rows.value[id]

    // Mirrors Room's @Upsert: the new row id on insert, -1 on update.
    override suspend fun upsert(alarm: AlarmEntity): Long {
        if (alarm.id != 0L && alarm.id in rows.value) {
            rows.value += alarm.id to alarm
            return -1
        }
        val id = if (alarm.id != 0L) alarm.id else nextId++
        rows.value += id to alarm.copy(id = id)
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        rows.value[id]?.let { rows.value += id to it.copy(enabled = enabled) }
    }

    override suspend fun delete(id: Long) {
        rows.value -= id
    }
}
