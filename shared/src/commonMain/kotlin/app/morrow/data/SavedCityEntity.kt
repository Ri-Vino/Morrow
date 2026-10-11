package app.morrow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morrow.places.City
import app.morrow.places.SavedCity

/** Holds everything about the city, so a saved city never needs the bundled list or the network again. */
@Entity(tableName = "saved_cities")
data class SavedCityEntity(
    /** The GeoNames id. */
    @PrimaryKey val id: Long,
    val name: String,
    val region: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "time_zone") val timeZone: String,
    val population: Long,
    @ColumnInfo(name = "is_home") val isHome: Boolean,
    /** Order on the World tab, smallest first. */
    val position: Int,
)

internal fun City.toEntity(isHome: Boolean, position: Int) =
    SavedCityEntity(id, name, region, country, latitude, longitude, timeZone, population, isHome, position)

internal fun SavedCityEntity.toSavedCity() =
    SavedCity(City(id, name, region, country, latitude, longitude, timeZone, population), isHome)
