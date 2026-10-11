package app.morrow.places

import app.morrow.data.CitySearchCacheDao
import app.morrow.data.CitySearchCacheEntity
import app.morrow.weather.FetchException
import app.morrow.weather.GeocodedPlace
import app.morrow.weather.Geocoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What online search has for a query. */
sealed interface OnlineSearch {
    /** Not searching online: weather is off, or under 2 characters are typed. */
    data object Off : OnlineSearch

    /** Waiting for the typing to stop, or for the answer. */
    data object Searching : OnlineSearch

    data class Found(val cities: List<City>) : OnlineSearch

    /** No answer, most likely because the phone is offline. */
    data object Failed : OnlineSearch
}

/**
 * Finding a city: the bundled list, and with weather on, Open-Meteo's geocoder for places too small for it.
 * Online answers are cached for 30 days. With weather off, nothing is ever sent.
 */
class CitySearch(
    private val catalog: CityCatalog,
    private val geocoder: Geocoder,
    private val cache: CitySearchCacheDao,
    private val weatherEnabled: Flow<Boolean>,
    private val clock: Clock,
) {
    suspend fun bundled(query: String, limit: Int): List<City> = catalog.search(query, limit)

    suspend fun popular(limit: Int, except: Set<Long> = emptySet()): List<City> = catalog.mostPopulous(limit, except)

    /** Whether [query] may go to the geocoder: weather is on and at least 2 characters are typed. */
    suspend fun mayGoOnline(query: String): Boolean = keyOf(query).length >= MIN_QUERY && weatherEnabled.first()

    /** What an earlier search for [query] found, if it's under 30 days old. Never uses the network. */
    suspend fun cached(query: String): List<City>? {
        if (!mayGoOnline(query)) return null
        val row = cache.get(keyOf(query)) ?: return null
        if (clock.now() - Instant.fromEpochMilliseconds(row.fetchedAt) > KeepFor) return null
        return try {
            Json.decodeFromString(PlacesSerializer, row.json).map { it.toCity() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Places matching [query] online, from the cache when it can be. [OnlineSearch.Off] without asking when it may not. */
    suspend fun online(query: String): OnlineSearch {
        if (!mayGoOnline(query)) return OnlineSearch.Off
        cached(query)?.let { return OnlineSearch.Found(it) }
        val places = try {
            geocoder.search(query.trim())
        } catch (_: FetchException) {
            return OnlineSearch.Failed
        }
        val now = clock.now()
        cache.deleteOlderThan((now - KeepFor).toEpochMilliseconds())
        cache.upsert(CitySearchCacheEntity(keyOf(query), Json.encodeToString(PlacesSerializer, places), now.toEpochMilliseconds()))
        return OnlineSearch.Found(places.map { it.toCity() })
    }

    private companion object {
        const val MIN_QUERY = 2
        val KeepFor = 30.days
        val PlacesSerializer = ListSerializer(GeocodedPlace.serializer())

        fun keyOf(query: String) = query.trim().lowercase()
    }
}

/** An online place as a city, which is all a saved city needs. Unknown populations count as 0, so they rank last. */
fun GeocodedPlace.toCity(): City = City(
    id = id,
    name = name,
    region = region?.takeIf { it.isNotBlank() },
    country = country.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    timeZone = timeZone,
    population = population ?: 0,
)

/**
 * One list of [bundled] and [online] results, most populous first. A place in both, by GeoNames id, appears once,
 * as the bundled list has it.
 */
fun mergeCities(bundled: List<City>, online: List<City>): List<City> {
    val ids = bundled.mapTo(HashSet()) { it.id }
    return (bundled + online.filter { ids.add(it.id) }).sortedByDescending { it.population }
}
