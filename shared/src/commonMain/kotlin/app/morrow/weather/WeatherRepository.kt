package app.morrow.weather

import app.morrow.data.LastLocation
import app.morrow.data.MorrowSettings
import app.morrow.data.WeatherCacheDao
import app.morrow.data.WeatherCacheEntity
import app.morrow.places.City
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
    /** The app starting or coming back to the foreground. A fix from the last [RefreshAfter] is used again. */
    START,

    /** The World tab opening. A fix from the last [RefreshAfter] is used again. */
    WORLD,

    /** The Morning screen, which waits only 5 s, for the location and the request together. */
    MORNING,

    /** Weather turned on, or "Where I am" / "Home city" changed. */
    SETTINGS,

    /**
     * Pulling World down, or the developer tools' "Refresh now": fetches every place, however fresh, except those
     * fetched in the last [ManualRefreshAfter], whose forecast can't have changed yet.
     */
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
    /** Where [forecasts] reads and decodes the cache. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    private val refreshing = Mutex()
    @Volatile private var waiting: RefreshReason? = null

    /**
     * Where "Where I am" weather is for, from what's stored: the last fix, the home city, or the city for the phone's
     * zone. Each fix [refresh] takes is saved first, so this follows it. Reading this never asks for the location.
     */
    val here: Flow<HerePlace?> = combine(settings.weatherSource, settings.lastLocation, cities.cities) { mode, last, saved ->
        resolveHere(mode, current = null, last = last, home = saved.firstOrNull { it.isHome }?.city)
    }.distinctUntilChanged()

    // By place, when its forecast was fetched and what that decoded to, shared by every screen reading [forecasts].
    private val decoded = HashMap<String, Pair<Long, Forecast?>>()
    private val decoding = Mutex()

    /**
     * The weather everywhere it shows, read off the main thread. Each place's forecast is decoded once for each time
     * it's fetched and kept for every screen, rather than again whenever a screen opens or anything changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val forecasts: Flow<WeatherState> = settings.weatherEnabled.distinctUntilChanged().flatMapLatest { enabled ->
        if (!enabled) {
            flowOf(WeatherState.Off)
        } else {
            combine(here, cities.cities, cache.observeAll()) { here, saved, rows ->
                val byKey = decodedAll(rows)
                val now = clock.now()
                fun weatherAt(place: Coordinates): PlaceWeather {
                    val forecast = byKey[place.key]
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
        .flowOn(work)

    /** Each cached row's forecast by place, decoding only rows fetched since they were last decoded. */
    private suspend fun decodedAll(rows: List<WeatherCacheEntity>): Map<String, Forecast?> = decoding.withLock {
        val keys = rows.mapTo(HashSet()) { it.placeKey }
        decoded.keys.retainAll(keys)
        rows.associate { row ->
            val known = decoded[row.placeKey]?.takeIf { it.first == row.fetchedAt }
            row.placeKey to (known ?: (row.fetchedAt to decode(row)).also { decoded[row.placeKey] = it }).second
        }
    }

    /** [refresh] in the app's scope, for callers that don't wait for it. */
    fun refreshSoon(reason: RefreshReason) {
        scope.launch { refresh(reason) }
    }

    private var prepared: Pair<Instant, Deferred<Instant?>>? = null

    /**
     * Starts the Morning refresh as the alarm rings, so the location fix and the request are done long before
     * [refreshForMorning] is asked for. Asking again while one is still running keeps that one; asking once it's done
     * starts again, as for an alarm ringing again after a snooze, though places fetched in the last [RefreshAfter]
     * come from the cache. With weather off it fetches nothing.
     */
    fun prepareMorning() {
        if (prepared?.second?.isActive == true) return
        prepared = clock.now() to scope.async {
            try {
                refresh(RefreshReason.MORNING)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * [refresh] for the Morning screen: takes up what [prepareMorning] started in the last [PreparedFor], once, and
     * waits for it if it is still running, rather than fixing the location and asking again.
     */
    suspend fun refreshForMorning(): Instant? {
        val early = prepared?.takeIf { clock.now() - it.first < PreparedFor }?.second
        prepared = null
        return if (early != null) early.await() else refresh(RefreshReason.MORNING)
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
                val last = settings.lastLocation.first()
                // Opening the app or World again soon after needn't turn the location on again.
                val reuseFix = reason in ReusesRecentFix && last != null && !needsRefresh(last.at, clock.now())
                val current = if (mode == WeatherPlaceMode.AUTO && !reuseFix) {
                    location.current(if (morning) MorningFixTimeout else RequestTimeout)?.rounded()
                } else {
                    null
                }
                val saved = cities.cities.first()
                val here = resolveHere(mode, current, last, saved.firstOrNull { it.isHome }?.city)
                val wanted = (listOfNotNull(here?.coordinates) + saved.map { it.city.coordinates }).distinct()
                val arrived = withTimeoutOrNull(timeoutFor(reason)) { download(stale(wanted, force = reason == RefreshReason.MANUAL)) }
                if (arrived != null) {
                    store(arrived)
                    storedHere = arrived.firstOrNull { it.place == here?.coordinates }?.fetchedAt
                    if (current != null) settings.saveLastLocation(current, clock.now(), here?.takeIf { it.coordinates == current }?.name)
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

    /** The [places] whose cache is older than 30 minutes, or when [force]d, older than [ManualRefreshAfter]. */
    private suspend fun stale(places: List<Coordinates>, force: Boolean): List<Coordinates> {
        val fetchedAt = cache.getAll().associate { it.placeKey to Instant.fromEpochMilliseconds(it.fetchedAt) }
        val now = clock.now()
        return places.filter { needsRefresh(fetchedAt[it.key], now, after = if (force) ManualRefreshAfter else RefreshAfter) }
    }

    private suspend fun download(places: List<Coordinates>): List<Forecast> = if (places.isEmpty()) emptyList() else source.forecast(places)

    private suspend fun store(forecasts: List<Forecast>) {
        if (forecasts.isEmpty()) return
        cache.upsert(forecasts.map { WeatherCacheEntity(it.place.key, OpenMeteoJson.encodeToString(Forecast.serializer(), it), it.fetchedAt.toEpochMilliseconds()) })
    }

    /**
     * [weatherPlace], with the name of the city it's in or nearest to, and how it was found. A fix at the [last] place
     * takes the name saved with it, as finding the nearest city means loading the whole list.
     */
    private suspend fun resolveHere(mode: WeatherPlaceMode, current: Coordinates?, last: LastLocation?, home: City?): HerePlace? {
        val zoneCity = if (home == null) catalog.cityForZone(phoneZone()) else null
        val place = weatherPlace(current, last?.coordinates, home?.coordinates, zoneCity?.coordinates, mode) ?: return null
        val fix = if (mode == WeatherPlaceMode.AUTO) current ?: last?.coordinates else null
        return when {
            fix != null && fix.rounded() == place -> {
                val name = last?.takeIf { it.coordinates == place }?.name ?: catalog.nearest(place.latitude, place.longitude)?.name
                HerePlace(place, name, HereSource.LOCATION)
            }
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

        /** How long a refresh started by [prepareMorning] counts for the Morning screen: as long as the cache does. */
        val PreparedFor = RefreshAfter

        /** The reasons that use a fix from the last [RefreshAfter] rather than asking for another. */
        val ReusesRecentFix = setOf(RefreshReason.START, RefreshReason.WORLD)

        /** Of [MorningTimeout], what finding the location may take. */
        val MorningFixTimeout = 2.seconds

        fun timeoutFor(reason: RefreshReason) = if (reason == RefreshReason.MORNING) MorningTimeout else RequestTimeout
    }
}
