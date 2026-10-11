package app.morrow.ui.world

import app.morrow.places.City
import app.morrow.places.SavedCity
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Everything the World tab shows, worked out from the saved cities and the time. */
data class WorldModel(
    /** "Your time 07:40 · London"; the screen sets it in capitals. */
    val header: String,
    val cards: List<WorldCard>,
)

data class WorldCard(
    val id: Long,
    val name: String,
    val isHome: Boolean,
    val clock: String,
    val relative: String,
    /** Minutes since midnight in the city, for its sky. */
    val minute: Int,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

/** What the sheet for one city shows. */
data class CitySheet(
    val id: Long,
    val name: String,
    /** "England, United Kingdom". */
    val place: String,
    val clock: String,
    val relative: String,
    val isHome: Boolean,
)

fun worldModel(cities: List<SavedCity>, now: Instant, phoneZone: TimeZone): WorldModel {
    val home = cities.firstOrNull { it.isHome }
    val header = buildString {
        append("Your time ").append(cityClock(now, phoneZone))
        if (home != null) append(" · ").append(home.city.name)
    }
    return WorldModel(
        header = header,
        cards = cities.mapIndexed { index, saved ->
            val zone = zoneOf(saved.city, phoneZone)
            WorldCard(
                id = saved.id,
                name = saved.city.name,
                isHome = saved.isHome,
                clock = cityClock(now, zone),
                relative = relativeDay(now, zone, phoneZone),
                minute = cityMinute(now, zone),
                canMoveUp = index > 0,
                canMoveDown = index < cities.lastIndex,
            )
        },
    )
}

fun citySheet(saved: SavedCity, now: Instant, phoneZone: TimeZone): CitySheet {
    val zone = zoneOf(saved.city, phoneZone)
    return CitySheet(
        id = saved.id,
        name = saved.city.name,
        place = placeName(saved.city),
        clock = cityClock(now, zone),
        relative = relativeDay(now, zone, phoneZone),
        isHome = saved.isHome,
    )
}

/** "Texas, United States", or just the country when there is no region. */
fun placeName(city: City): String = listOfNotNull(city.region, city.country).joinToString(", ")

/** The city's zone, or the phone's if the id isn't one this phone knows. */
internal fun zoneOf(city: City, fallback: TimeZone): TimeZone = runCatching { TimeZone.of(city.timeZone) }.getOrDefault(fallback)
