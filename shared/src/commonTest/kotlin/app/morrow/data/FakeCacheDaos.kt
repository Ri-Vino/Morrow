package app.morrow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWeatherCacheDao : WeatherCacheDao {
    val rows = MutableStateFlow<Map<String, WeatherCacheEntity>>(emptyMap())

    override fun observeAll(): Flow<List<WeatherCacheEntity>> = rows.map { it.values.toList() }

    override suspend fun getAll(): List<WeatherCacheEntity> = rows.value.values.toList()

    override suspend fun upsert(rows: List<WeatherCacheEntity>) {
        this.rows.value += rows.associateBy { it.placeKey }
    }

    override suspend fun deleteAllExcept(keep: List<String>) {
        rows.value = rows.value.filterKeys { it in keep }
    }
}

class FakeCitySearchCacheDao : CitySearchCacheDao {
    val rows = mutableMapOf<String, CitySearchCacheEntity>()

    override suspend fun get(query: String): CitySearchCacheEntity? = rows[query]

    override suspend fun upsert(row: CitySearchCacheEntity) {
        rows[row.query] = row
    }

    override suspend fun deleteOlderThan(before: Long) {
        rows.values.removeAll { it.fetchedAt < before }
    }
}
