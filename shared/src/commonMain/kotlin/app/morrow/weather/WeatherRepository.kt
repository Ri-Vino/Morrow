package app.morrow.weather

import app.morrow.data.MorrowSettings
import app.morrow.data.WeatherCacheDao
import app.morrow.data.WeatherCacheEntity
import app.morrow.places.City
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Why the weather is being refreshed. Every one of them happens with the app open. */
enum class RefreshReason {
    /** The app starting or coming back to the foreground. */
    START,

    /** The World tab opening. */
    WORLD,

    /** The Morning screen, which waits only 5 s, for the location and the request together. */
    MORNING,

    /** Weather turned on, or "Where I am" / "Home city" changed. */
    SETTINGS,

    /** The developer tools' "Refresh now": fetches every place, however fresh. */
    MANUAL,
}

/** How the weather place was found. */
enum class HereSource { LOCATION, HOME_CITY, ZONE_CITY }

/** The place whose weather Home shows. [name] is the city it's in or nearest to. */
data class HerePlace(val coordinates: Coordinates, val name: String?, val from: HereSource)

/** The cached weather for one place, and how old it was when this was read. */
data class PlaceWeather(val place: Coordinates, val forecast: Forecast?, val age: WeatherAge) {
    /** The age at [now], for a screen whose clock ticks on after this was read. */
    fun ageAt(now: Instant): WeatherAge = weatherAge(forecast?.fetchedAt, now)
}

/** The weather everywhere it shows: [here], and each saved city by its id. With weather off, there is none. */
data class WeatherState(
    val enabled: Boolean,
    val here: HerePlace?,
    val hereWeather: PlaceWeather?,
    val cities: Map<Long, PlaceWeather>,
) {
    companion object {
        val Off = WeatherState(enabled = false, here = null, hereWeather = null, cities = emptyMap())
    }
}

/** A saved or bundled city's place, rounded, as forecasts are asked for and cached. */
val City.coordinates: Coordinates get() = Coordinates(latitude, longitude).rounded()

/**
 * Forecasts for where the phone is and for each saved city, cached in the database.
 * Nothing here reads the location or uses the network while weather is off, or for an hour after a 429.
 * [phoneZone] is the phone's IANA zone id; [scope] runs [refreshSoon], and should live as long as the app.
 */
class WeatherRepository(
    private val settings: MorrowSettings,
    private val cities: CityRepository,
    private val catalog: CityCatalog,
    private val location: LocationProvider,
    private val source: WeatherSource,
    private val cache: WeatherCacheDao,
    private val clock: Clock,
    private val phoneZone: () -> String,
    private val scope: CoroutineScope,
) {
    private val refreshing = Mutex()
    @Volatile private var waiting: RefreshReason? = null

    /**
     * Where "Where I am" weather is for, from what's stored: the last fix, the home city, or the city for the phone's
     * zone. Each fix [refresh] takes is saved first, so this follows it. Reading this never asks for the location.
     */
    val here: Flow<HerePlace?> = combine(settings.weatherSource, settings.lastLocation, cities.cities) { mode, last, saved ->
        resolveHere(mode, current = null, last = last?.coordinates, home = saved.firstOrNull { it.isHome }?.city)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val forecasts: Flow<WeatherState> = settings.weatherEnabled.distinctUntilChanged().flatMapLatest { enabled ->
        if (!enabled) {
            flowOf(WeatherState.Off)
        } else {
            combine(here, cities.cities, cache.observeAll()) { here, saved, rows ->
                val byKey = rows.associateBy { it.placeKey }
                val now = clock.now()
                fun weatherAt(place: Coordinates): PlaceWeather {
                    val forecast = byKey[place.key]?.let(::decode)
                    return PlaceWeather(place, forecast, weatherAge(forecast?.fetchedAt, now))
                }
                WeatherState(
                    enabled = true,
                    here = here,
                    hereWeather = here?.let { weatherAt(it.coordinates) },
                    cities = saved.associate { it.id to weatherAt(it.city.coordinates) },
                )
            }
        }
    }

    /** [refresh] in the app's scope, for callers that don't wait for it. */
    fun refreshSoon(reason: RefreshReason) {
        scope.launch { refresh(reason) }
    }

    /** [refreshCity] in the app's scope. */
    fun refreshCitySoon(city: City) {
        scope.launch { refreshCity(city) }
    }

    /**
     * Fetches, in one request, every place whose cache is older than 30 minutes: here, then each saved city.
     * Does nothing at all with weather off or while backing off. One asked for while another runs follows it, as
     * the place may have changed since the running one began. Failures are silent and keep the cache. The fix and the
     * request each get 10 s. [RefreshReason.MORNING] gets 5 s for both together, of which the fix may take 2 s.
     *
     * A new fix becomes "here" only once the request has finished, so a request that fails or runs out of time leaves
     * the last place's weather showing, and the cache is pruned of places no longer asked about only then.
     *
     * Returns when the forecast it stored for "here" was fetched, or null when it stored none for it. The database
     * passes a write on to [forecasts] a moment later, so this tells a screen what to wait for.
     */
    suspend fun refresh(reason: RefreshReason): Instant? {
        if (!refreshing.tryLock()) {
            waiting = reason
            return null
        }
        var storedHere: Instant? = null
        try {
            if (!mayFetch()) return null
            val morning = reason == RefreshReason.MORNING
            val mode = settings.weatherSource.first()
            val locateAndFetch: suspend () -> Unit = {
                val current = if (mode == WeatherPlaceMode.AUTO) location.current(if (morning) MorningFixTimeout else RequestTimeout)?.rounded() else null
                val saved = cities.cities.first()
                val here = resolveHere(mode, current, settings.lastLocation.first()?.coordinates, saved.firstOrNull { it.isHome }?.city)
                val wanted = (listOfNotNull(here?.coordinates) + saved.map { it.city.coordinates }).distinct()
                val arrived = withTimeoutOrNull(timeoutFor(reason)) { download(stale(wanted, force = reason == RefreshReason.MANUAL)) }
                if (arrived != null) {
                    store(arrived)
                    storedHere = arrived.firstOrNull { it.place == here?.coordinates }?.fetchedAt
                    if (current != null) settings.saveLastLocation(current, clock.now())
                    cache.deleteAllExcept(wanted.map(Coordinates::key))
                }
            }
            try {
                // Morning has 5 s for everything. A fix that doesn't come in its share leaves the last fix, the home
                // city or the zone's city, and the rest of the 5 s for the request.
                if (morning) withTimeoutOrNull(MorningTimeout) { locateAndFetch() } else locateAndFetch()
            } catch (e: FetchException) {
                if (e.rateLimited) settings.setWeatherBackoffUntil(backoffUntil(clock.now()))
            }
        } finally {
            refreshing.unlock()
        }
        waiting?.let { next ->
            waiting = null
            refresh(next)
        }
        return storedHere
    }

    /** Fetches just [city], newly added, unless its cache is fresh. Waits for a running refresh rather than skipping. */
    suspend fun refreshCity(city: City) {
        refreshing.withLock {
            if (mayFetch()) fetch(listOf(city.coordinates), force = false, timeout = RequestTimeout)
        }
    }

    private suspend fun mayFetch(): Boolean =
        settings.weatherEnabled.first() && !isBackingOff(settings.weatherBackoffUntil.first(), clock.now())

    private suspend fun fetch(places: List<Coordinates>, force: Boolean, timeout: Duration) {
        val forecasts = try {
            withTimeoutOrNull(timeout) { download(stale(places, force)) } ?: return
        } catch (e: FetchException) {
            if (e.rateLimited) settings.setWeatherBackoffUntil(backoffUntil(clock.now()))
            return
        }
        store(forecasts)
    }

    /** The [places] whose cache is older than 30 minutes, or all of them when [force]d. */
    private suspend fun stale(places: List<Coordinates>, force: Boolean): List<Coordinates> {
        val fetchedAt = cache.getAll().associate { it.placeKey to Instant.fromEpochMilliseconds(it.fetchedAt) }
        val now = clock.now()
        return places.filter { force || needsRefresh(fetchedAt[it.key], now) }
    }

    private suspend fun download(places: List<Coordinates>): List<Forecast> = if (places.isEmpty()) emptyList() else source.forecast(places)

    private suspend fun store(forecasts: List<Forecast>) {
        if (forecasts.isEmpty()) return
        cache.upsert(forecasts.map { WeatherCacheEntity(it.place.key, OpenMeteoJson.encodeToString(Forecast.serializer(), it), it.fetchedAt.toEpochMilliseconds()) })
    }

    /** [weatherPlace], with the name of the city it's in or nearest to, and how it was found. */
    private suspend fun resolveHere(mode: WeatherPlaceMode, current: Coordinates?, last: Coordinates?, home: City?): HerePlace? {
        val zoneCity = if (home == null) catalog.cityForZone(phoneZone()) else null
        val place = weatherPlace(current, last, home?.coordinates, zoneCity?.coordinates, mode) ?: return null
        val fix = if (mode == WeatherPlaceMode.AUTO) current ?: last else null
        return when {
            fix != null && fix.rounded() == place -> HerePlace(place, catalog.nearest(place.latitude, place.longitude)?.name, HereSource.LOCATION)
            home != null -> HerePlace(place, home.name, HereSource.HOME_CITY)
            else -> HerePlace(place, zoneCity?.name, HereSource.ZONE_CITY)
        }
    }

    private fun decode(row: WeatherCacheEntity): Forecast? = try {
        OpenMeteoJson.decodeFromString(Forecast.serializer(), row.json)
    } catch (_: IllegalArgumentException) {
        // A cache written by an older Forecast; the next refresh replaces it.
        null
    }

    private companion object {
        val RequestTimeout = 10.seconds
        val MorningTimeout = 5.seconds

        /** Of [MorningTimeout], what finding the location may take. */
        val MorningFixTimeout = 2.seconds

        fun timeoutFor(reason: RefreshReason) = if (reason == RefreshReason.MORNING) MorningTimeout else RequestTimeout
    }
}
