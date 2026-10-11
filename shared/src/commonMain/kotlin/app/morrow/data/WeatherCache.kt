package app.morrow.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The last forecast for one rounded place, as JSON, so weather shows offline and refreshes stay rare. */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    /** [app.morrow.weather.Coordinates.key], like "51.51,-0.13". */
    @PrimaryKey @ColumnInfo(name = "place_key") val placeKey: String,
    val json: String,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache")
    fun observeAll(): Flow<List<WeatherCacheEntity>>

    @Query("SELECT * FROM weather_cache")
    suspend fun getAll(): List<WeatherCacheEntity>

    @Upsert
    suspend fun upsert(rows: List<WeatherCacheEntity>)

    /** Forgets places no longer asked about, such as a removed city or somewhere the phone has left. */
    @Query("DELETE FROM weather_cache WHERE place_key NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)
}

/** What online city search found for one query, as JSON, kept for 30 days. */
@Entity(tableName = "city_search_cache")
data class CitySearchCacheEntity(
    /** The query, trimmed and lower-cased. */
    @PrimaryKey val query: String,
    val json: String,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Dao
interface CitySearchCacheDao {
    @Query("SELECT * FROM city_search_cache WHERE `query` = :query")
    suspend fun get(query: String): CitySearchCacheEntity?

    @Upsert
    suspend fun upsert(row: CitySearchCacheEntity)

    @Query("DELETE FROM city_search_cache WHERE fetched_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
