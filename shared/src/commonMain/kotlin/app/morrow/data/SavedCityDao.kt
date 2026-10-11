package app.morrow.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCityDao {
    @Query("SELECT * FROM saved_cities ORDER BY position")
    fun observeAll(): Flow<List<SavedCityEntity>>

    @Query("SELECT * FROM saved_cities ORDER BY position")
    suspend fun getAll(): List<SavedCityEntity>

    /** Writes every row in one transaction. */
    @Upsert
    suspend fun upsert(cities: List<SavedCityEntity>)

    @Query("DELETE FROM saved_cities WHERE id = :id")
    suspend fun delete(id: Long)
}
