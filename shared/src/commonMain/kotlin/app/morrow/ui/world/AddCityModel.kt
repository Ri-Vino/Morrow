package app.morrow.ui.world

import app.morrow.places.City
import app.morrow.places.OnlineSearch
import app.morrow.places.mergeCities
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** How many places from the bundled list the Add a city list shows at most. Online results join them. */
const val ADD_CITY_LIMIT = 8

/** What picking a city does: add it to the World tab, or make it the home city from Settings. */
enum class CityPickPurpose { ADD, HOME }

data class AddCityModel(
    /** Whether the "Popular" label heads the list, which it does while nothing has been typed. */
    val showPopularLabel: Boolean,
    val rows: List<AddCityRow>,
    /** Something was typed and nothing matches it, with nothing more on the way. */
    val nothingFound: Boolean,
    /** "Looking for more places…": online results are on their way. */
    val searchingMore: Boolean,
    /** Online search is on, so places beyond the bundled list can turn up. */
    val searchingOnline: Boolean,
    /** Online search failed: "You're offline…". */
    val offline: Boolean,
)

data class AddCityRow(
    val city: City,
    val name: String,
    /** "England, United Kingdom". */
    val place: String,
    /** The place's time now. */
    val clock: String,
    /** Already done: saved to the World tab, or already the home city. It shows a tick in place of the button. */
    val saved: Boolean,
    /** What TalkBack calls the button, or the tick when [saved]. */
    val label: String,
)

/**
 * The Add a city list. [found] is what the bundled list has for [query], or, with nothing typed, the most populous
 * cities. [online] is what online search has for the same query; its places join the list, ranked by population.
 * Adding, with nothing typed the cities already saved are left out, since they're no use as suggestions; typed searches
 * keep them, ticked, so a search for a saved city says it is saved rather than that it doesn't exist.
 * Picking a home city, [homeId]'s city is ticked and every other one can be picked.
 */
fun addCityModel(
    query: String,
    found: List<City>,
    savedIds: Set<Long>,
    now: Instant,
    phoneZone: TimeZone,
    online: OnlineSearch = OnlineSearch.Off,
    purpose: CityPickPurpose = CityPickPurpose.ADD,
    homeId: Long? = null,
): AddCityModel {
    val typed = query.isNotBlank()
    val adding = purpose == CityPickPurpose.ADD
    val bundled = (if (typed || !adding) found else found.filter { it.id !in savedIds }).take(ADD_CITY_LIMIT)
    val shown = if (typed && online is OnlineSearch.Found) mergeCities(bundled, online.cities) else bundled
    val searching = typed && online == OnlineSearch.Searching
    return AddCityModel(
        showPopularLabel = !typed && shown.isNotEmpty(),
        rows = shown.map { city ->
            val done = if (adding) city.id in savedIds else city.id == homeId
            AddCityRow(
                city = city,
                name = city.name,
                place = placeName(city),
                clock = cityClock(now, zoneOf(city, phoneZone)),
                saved = done,
                label = when {
                    adding && done -> "${city.name} is already in your cities"
                    adding -> "Add ${city.name}"
                    done -> "${city.name} is your home city"
                    else -> "Make ${city.name} your home city"
                },
            )
        },
        nothingFound = typed && shown.isEmpty() && !searching,
        searchingMore = searching,
        searchingOnline = typed && (online == OnlineSearch.Searching || online is OnlineSearch.Found),
        offline = typed && online == OnlineSearch.Failed,
    )
}
