package app.morrow.places

import app.morrow.data.SavedCityDao
import app.morrow.data.SavedCityEntity
import app.morrow.data.toEntity
import app.morrow.data.toSavedCity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The cities on the World tab: home first, then the rest in the user's order. [phoneZone] is the phone's IANA zone id,
 * for the first home city.
 */
class CityRepository(
    private val dao: SavedCityDao,
    private val catalog: CityCatalog,
    private val phoneZone: () -> String,
) {
    // Each change reads the rows and writes them back, so changes take turns.
    private val writing = Mutex()

    val cities: Flow<List<SavedCity>> = dao.observeAll().map { rows -> homeFirst(rows).map { it.toSavedCity() } }

    /** On first use, saves the phone's own city as home. Does nothing once any city is saved. */
    suspend fun ensureSeeded() = writing.withLock {
        if (dao.getAll().isNotEmpty()) return@withLock
        val city = catalog.cityForZone(phoneZone()) ?: return@withLock
        dao.upsert(listOf(city.toEntity(isHome = true, position = 0)))
    }

    /** Adds [city] at the end, unless it's already saved. With no home city yet, it becomes home. */
    suspend fun add(city: City) = writing.withLock {
        val rows = dao.getAll()
        if (rows.any { it.id == city.id }) return@withLock
        val position = (rows.maxOfOrNull { it.position } ?: -1) + 1
        dao.upsert(listOf(city.toEntity(isHome = rows.none { it.isHome }, position = position)))
    }

    /** Removes a saved city. The home city stays: make another city home first. */
    suspend fun remove(id: Long) = writing.withLock {
        val row = dao.getAll().firstOrNull { it.id == id } ?: return@withLock
        if (!row.isHome) dao.delete(id)
    }

    /** Makes [id] the home city, which goes to the top. */
    suspend fun makeHome(id: Long) = writing.withLock {
        val rows = dao.getAll()
        if (rows.none { it.id == id }) return@withLock
        dao.upsert(renumber(homeFirst(rows.map { it.copy(isHome = it.id == id) }), rows))
    }

    /** Moves a city [by] places, negative for up, stopping at either end. Home stays at the top and never moves. */
    suspend fun move(id: Long, by: Int) = writing.withLock {
        val saved = dao.getAll()
        val rows = homeFirst(saved).toMutableList()
        val from = rows.indexOfFirst { it.id == id }
        if (from < 0 || rows[from].isHome) return@withLock
        val row = rows.removeAt(from)
        val top = if (rows.firstOrNull()?.isHome == true) 1 else 0
        rows.add((from + by).coerceIn(top, rows.size), row)
        dao.upsert(renumber(rows, saved))
    }

    suspend fun home(): SavedCity? = dao.getAll().firstOrNull { it.isHome }?.toSavedCity()

    /** Home, wherever an older version saved it, then the rest in order. */
    private fun homeFirst(rows: List<SavedCityEntity>) = rows.sortedBy { !it.isHome }

    /** [rows] numbered 0, 1, 2… in this order, leaving out any that are the same as [saved]. */
    private fun renumber(rows: List<SavedCityEntity>, saved: List<SavedCityEntity>) =
        rows.mapIndexed { position, row -> row.copy(position = position) }.filter { it !in saved }
}
