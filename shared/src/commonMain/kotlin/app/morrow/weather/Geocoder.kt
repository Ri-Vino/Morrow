package app.morrow.weather

import app.morrow.platform.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement

/** Online city search, for places too small for the bundled list. */
interface Geocoder {
    /** Places matching [query], best first. Throws [FetchException] if the search fails. */
    suspend fun search(query: String): List<GeocodedPlace>
}

/** A place from online search. [id] is its GeoNames id, the same id the bundled list uses. */
@Serializable
data class GeocodedPlace(
    val id: Long,
    val name: String,
    /** The state, province or region, like "England". */
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    /** IANA id, like "Europe/London". */
    val timeZone: String,
    val population: Long?,
)

/** Searches geocoding-api.open-meteo.com. Sends only the search text, and nothing for under 2 characters. */
class OpenMeteoGeocoder(private val http: HttpClient) : Geocoder {

    override suspend fun search(query: String): List<GeocodedPlace> {
        val text = query.trim()
        if (text.length < MIN_QUERY) return emptyList()
        val reply = http.getJson(searchUrl(text))
        val results = readOrFail { OpenMeteoJson.decodeFromJsonElement<SearchDto>(reply) }.results
        // A place without a time zone is no use to a clock app.
        return results.mapNotNull { it.toPlace() }
    }

    internal companion object {
        const val MIN_QUERY = 2

        fun searchUrl(text: String): String =
            "https://geocoding-api.open-meteo.com/v1/search?name=${urlEncode(text)}&count=10&language=en&format=json"
    }
}

private fun ResultDto.toPlace(): GeocodedPlace? = GeocodedPlace(
    id = id,
    name = name,
    region = admin1,
    country = country,
    latitude = latitude,
    longitude = longitude,
    timeZone = timezone ?: return null,
    population = population,
)

/** No matches come back with no "results" at all. */
@Serializable
private class SearchDto(val results: List<ResultDto> = emptyList())

@Serializable
private class ResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val admin1: String? = null,
    val country: String? = null,
    val population: Long? = null,
)
