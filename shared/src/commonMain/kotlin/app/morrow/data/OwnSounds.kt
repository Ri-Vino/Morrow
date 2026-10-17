package app.morrow.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import app.morrow.domain.OwnSound
import app.morrow.domain.RingSound
import app.morrow.domain.encode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "own_sounds")
data class OwnSoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    /** Where the copy is kept, in storage that's there before the phone's first unlock. */
    val path: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Dao
interface OwnSoundDao {
    @Query("SELECT * FROM own_sounds ORDER BY added_at")
    fun observeAll(): Flow<List<OwnSoundEntity>>

    @Query("SELECT * FROM own_sounds WHERE id = :id")
    suspend fun get(id: Long): OwnSoundEntity?

    @Insert
    suspend fun insert(sound: OwnSoundEntity): Long

    @Query("DELETE FROM own_sounds WHERE id = :id")
    suspend fun delete(id: Long)
}

/** The sounds the user has added from their phone, oldest first, to pick for any alarm. */
class OwnSounds(private val dao: OwnSoundDao, private val alarms: AlarmDao) {

    val sounds: Flow<List<OwnSound>> = dao.observeAll().map { rows -> rows.map { it.toOwnSound() } }

    suspend fun get(id: Long): OwnSound? = dao.get(id)?.toOwnSound()

    suspend fun add(name: String, path: String, addedAt: Long): OwnSound {
        val id = dao.insert(OwnSoundEntity(0, name, path, addedAt))
        return OwnSound(id, name, path)
    }

    /**
     * Takes [id] off the list. Alarms that rang with it ring with the default sound again. Returns it, so its copy can
     * be deleted too, or null if it was already gone.
     */
    suspend fun remove(id: Long): OwnSound? {
        val sound = get(id) ?: return null
        dao.delete(id)
        alarms.clearSound(RingSound.Own(id).encode())
        return sound
    }
}

private fun OwnSoundEntity.toOwnSound() = OwnSound(id, name, path)
