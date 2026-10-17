package app.morrow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeOwnSoundDao : OwnSoundDao {
    private val rows = MutableStateFlow<Map<Long, OwnSoundEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<OwnSoundEntity>> = rows.map { it.values.sortedBy(OwnSoundEntity::addedAt) }

    override suspend fun get(id: Long): OwnSoundEntity? = rows.value[id]

    override suspend fun insert(sound: OwnSoundEntity): Long {
        val id = nextId++
        rows.value += id to sound.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        rows.value -= id
    }
}
