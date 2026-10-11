package app.morrow.ui.world

import app.morrow.places.City
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** How many places the Add a city list shows at most. */
const val ADD_CITY_LIMIT = 8

data class AddCityModel(
    /** Whether the "Popular" label heads the list, which it does while nothing has been typed. */
    val showPopularLabel: Boolean,
    val rows: List<AddCityRow>,
    /** Something was typed and nothing matches it. */
    val nothingFound: Boolean,
)

data class AddCityRow(
    val city: City,
    val name: String,
    /** "England, United Kingdom". */
    val place: String,
    /** The place's time now. */
    val clock: String,
    /** Already on the World tab, so it shows a tick in place of the add button. */
    val saved: Boolean,
)

/**
 * The Add a city list. [found] is what the catalog returned for [query], or, with nothing typed, the most populous cities.
 * With nothing typed the cities already saved are left out, since they're no use as suggestions; typed searches keep
 * them, ticked, so a search for a saved city says it is saved rather than that it doesn't exist.
 */
fun addCityModel(query: String, found: List<City>, savedIds: Set<Long>, now: Instant, phoneZone: TimeZone): AddCityModel {
    val typed = query.isNotBlank()
    val shown = (if (typed) found else found.filter { it.id !in savedIds }).take(ADD_CITY_LIMIT)
    return AddCityModel(
        showPopularLabel = !typed && shown.isNotEmpty(),
        rows = shown.map { city ->
            AddCityRow(
                city = city,
                name = city.name,
                place = placeName(city),
                clock = cityClock(now, zoneOf(city, phoneZone)),
                saved = city.id in savedIds,
            )
        },
        nothingFound = typed && shown.isEmpty(),
    )
}
