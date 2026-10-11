package app.morrow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSavedCityDao : SavedCityDao {
    private val rows = MutableStateFlow<Map<Long, SavedCityEntity>>(emptyMap())

    override fun observeAll(): Flow<List<SavedCityEntity>> = rows.map { it.values.sortedBy(SavedCityEntity::position) }

    override suspend fun getAll(): List<SavedCityEntity> = rows.value.values.sortedBy(SavedCityEntity::position)

    override suspend fun upsert(cities: List<SavedCityEntity>) {
        rows.value += cities.associateBy { it.id }
    }

    override suspend fun delete(id: Long) {
        rows.value -= id
    }
}
