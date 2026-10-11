package app.morrow.places

import app.morrow.domain.ZoneRenames
import app.morrow.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.cos

/**
 * The cities bundled with the app, from GeoNames, so finding a city works without the network.
 * [load] returns the list as tools/make_cities.py writes it, and runs the first time it's needed.
 */
class CityCatalog(private val load: suspend () -> String) {

    @Volatile
    private var loaded: CityTable? = null
    private val loading = Mutex()

    /**
     * Cities whose name, or one of its words, starts with [query], ignoring case, most populous first.
     * The ASCII name counts too, so "reykjavik" finds Reykjavík.
     */
    suspend fun search(query: String, limit: Int = 6): List<City> {
        val text = query.trim().lowercase()
        if (text.isEmpty() || limit <= 0) return emptyList()
        val table = table()
        val found = mutableListOf<City>()
        for (i in 0 until table.size) {
            if (startsAnyWord(table.searchNames[i], text) || table.searchAsciiNames[i]?.let { startsAnyWord(it, text) } == true) {
                found += table[i]
                if (found.size == limit) break
            }
        }
        return found
    }

    /** The [limit] most populous cities, skipping any whose id is in [except], to suggest places to add. */
    suspend fun mostPopulous(limit: Int, except: Set<Long> = emptySet()): List<City> {
        val table = table()
        val found = mutableListOf<City>()
        var i = 0
        while (i < table.size && found.size < limit) {
            if (table.ids[i] !in except) found += table[i]
            i++
        }
        return found
    }

    /** The city closest to a point, to name a location fix. */
    suspend fun nearest(latitude: Double, longitude: Double): City? {
        val table = table()
        var best = -1
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until table.size) {
            val distance = squaredDistance(latitude, longitude, table.latitudes[i], table.longitudes[i])
            if (distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return if (best < 0) null else table[best]
    }

    /** The most populous city in a time zone, such as London for "Europe/London". Old names like "Asia/Calcutta" work too. */
    suspend fun cityForZone(zoneId: String): City? {
        val table = table()
        val zones = setOfNotNull(zoneId, ZoneRenames[zoneId])
        for (i in 0 until table.size) {
            if (table.zones[i] in zones) return table[i]
        }
        return null
    }

    suspend fun byId(id: Long): City? {
        val table = table()
        val i = table.ids.indexOf(id)
        return if (i < 0) null else table[i]
    }

    private suspend fun table(): CityTable =
        loaded ?: loading.withLock {
            loaded ?: withContext(Dispatchers.Default) { CityTable.of(parseCities(load())) }.also { loaded = it }
        }
}

/** The catalog of the list in the app's resources. */
fun bundledCityCatalog(): CityCatalog = CityCatalog { Res.readBytes("files/cities.tsv").decodeToString() }

/**
 * Reads the list tools/make_cities.py writes: one city per line, with the tab-separated columns id, name, ASCII name
 * (empty when it's the name), region (may be empty), country, latitude, longitude, zone and population.
 * Lines that don't fit are skipped.
 */
internal fun parseCities(tsv: String): List<ParsedCity> {
    val cities = ArrayList<ParsedCity>(tsv.length / 70)
    // Most regions, countries and zones repeat, so keep one copy of each.
    val shared = HashMap<String, String>()
    fun share(text: String) = shared.getOrPut(text) { text }
    for (line in tsv.lineSequence()) {
        val columns = line.split('\t')
        if (columns.size != 9) continue
        val id = columns[0].toLongOrNull() ?: continue
        val latitude = columns[5].toDoubleOrNull() ?: continue
        val longitude = columns[6].toDoubleOrNull() ?: continue
        val population = columns[8].toLongOrNull() ?: continue
        val name = columns[1].trim()
        if (name.isEmpty() || columns[7].isBlank()) continue
        val city = City(
            id = id,
            name = name,
            region = columns[3].trim().takeIf { it.isNotEmpty() }?.let(::share),
            country = share(columns[4].trim()),
            latitude = latitude,
            longitude = longitude,
            timeZone = share(columns[7].trim()),
            population = population,
        )
        cities += ParsedCity(city, columns[2].trim().takeIf { it.isNotEmpty() && it != name })
    }
    return cities
}

internal class ParsedCity(val city: City, val asciiName: String?)

/** The list in columns, most populous first, which keeps 34k cities small and quick to scan. */
internal class CityTable private constructor(sorted: List<ParsedCity>) {
    val size = sorted.size
    val ids = LongArray(size) { sorted[it].city.id }
    private val names = Array(size) { sorted[it].city.name }
    private val regions = Array(size) { sorted[it].city.region }
    private val countries = Array(size) { sorted[it].city.country }
    val latitudes = DoubleArray(size) { sorted[it].city.latitude }
    val longitudes = DoubleArray(size) { sorted[it].city.longitude }
    val zones = Array(size) { sorted[it].city.timeZone }
    private val populations = LongArray(size) { sorted[it].city.population }
    val searchNames = Array(size) { names[it].lowercase() }
    val searchAsciiNames = Array(size) { i -> sorted[i].asciiName?.lowercase()?.takeIf { it != searchNames[i] } }

    operator fun get(i: Int) = City(ids[i], names[i], regions[i], countries[i], latitudes[i], longitudes[i], zones[i], populations[i])

    companion object {
        fun of(cities: List<ParsedCity>) = CityTable(cities.sortedByDescending { it.city.population })
    }
}

/** Whether [prefix] starts [name] or one of its words, as "york" does "new york" and "denis" does "saint-denis". */
internal fun startsAnyWord(name: String, prefix: String): Boolean {
    if (name.startsWith(prefix)) return true
    for (i in 1..name.length - prefix.length) {
        if (!name[i - 1].isLetterOrDigit() && name[i].isLetterOrDigit() && name.startsWith(prefix, i)) return true
    }
    return false
}

/** Equirectangular: plenty to tell which of two cities is closer, and it wraps round the date line. */
private fun squaredDistance(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
    var east = longitude2 - longitude1
    if (east > 180) east -= 360 else if (east < -180) east += 360
    val x = east * cos((latitude1 + latitude2) / 2 * PI / 180)
    val y = latitude2 - latitude1
    return x * x + y * y
}
