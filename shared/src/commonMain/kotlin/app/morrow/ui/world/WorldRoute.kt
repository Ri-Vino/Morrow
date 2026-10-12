package app.morrow.ui.world

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.places.City
import app.morrow.places.CitySearch
import app.morrow.places.OnlineSearch
import app.morrow.places.CityRepository
import app.morrow.places.SavedCity
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.minuteTicks
import app.morrow.weather.Units
import app.morrow.weather.WeatherState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The World tab over the saved cities and their [weather], shown in [units]. [ticks] is the time, then each minute
 * after; the clock's by default.
 */
@Composable
fun WorldRoute(
    cities: CityRepository,
    onAddCity: () -> Unit,
    modifier: Modifier = Modifier,
    /** A new alarm on a city's clock, or on the phone's when the city keeps the same time. */
    onNewAlarm: (City?) -> Unit = {},
    /** Whether a city's sheet is open, so the tab bar can step aside for its buttons. */
    onSheetChange: (open: Boolean) -> Unit = {},
    weather: Flow<WeatherState> = NoWeather,
    units: Flow<Units> = InCelsius,
    ticks: Flow<Instant>? = null,
    phoneZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val scope = rememberCoroutineScope()
    val saved by cities.cities.collectAsStateWithLifecycle<List<SavedCity>?>(initialValue = null)
    val forecasts by weather.collectAsStateWithLifecycle<WeatherState?>(initialValue = null)
    val unit by units.collectAsStateWithLifecycle(initialValue = Units.CELSIUS)
    val now by remember(ticks) { ticks ?: minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }

    val list = saved ?: return
    val open = openId?.let { id -> list.firstOrNull { it.id == id } }
    val sheetOpen = open != null
    DisposableEffect(sheetOpen) {
        onSheetChange(sheetOpen)
        onDispose { onSheetChange(false) }
    }
    WorldScreen(
        model = worldModel(list, now, phoneZone, forecasts, unit),
        sheet = open?.let { citySheet(it, now, phoneZone, forecasts, unit) },
        actions = WorldActions(
            onAddCity = onAddCity,
            onOpenCity = { openId = it },
            onCloseCity = { openId = null },
            onMakeHome = { id ->
                openId = null
                scope.launch { cities.makeHome(id) }
            },
            onRemove = { id ->
                openId = null
                scope.launch { cities.remove(id) }
            },
            onMove = { id, by -> scope.launch { cities.move(id, by) } },
            onNewAlarm = { id ->
                openId = null
                list.firstOrNull { it.id == id }?.city?.let { city -> onNewAlarm(city.takeIf { zoneOf(it, phoneZone) != phoneZone }) }
            },
        ),
        modifier = modifier,
    )
}

/**
 * Add a city: search the bundled list, and online with weather on. Tapping + saves the city and goes [onBack] to World.
 * With [purpose] HOME, from Settings, the picked city is saved if it isn't already and becomes home.
 * [onPicked] hears about the city before going back.
 */
@Composable
fun AddCityRoute(
    search: CitySearch,
    cities: CityRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    purpose: CityPickPurpose = CityPickPurpose.ADD,
    onPicked: (City) -> Unit = {},
    ticks: Flow<Instant>? = null,
    phoneZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val scope = rememberCoroutineScope()
    PlatformBackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val saved by cities.cities.collectAsStateWithLifecycle(initialValue = emptyList())
    val now by remember(ticks) { ticks ?: minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    val savedIds = saved.mapTo(HashSet()) { it.id }
    var found by remember { mutableStateOf<Found?>(null) }
    var online by remember { mutableStateOf<Online?>(null) }

    // The list follows the typing a moment behind, so a fast typist doesn't search for every letter.
    val text = query.trim()
    LaunchedEffect(text, savedIds) {
        if (text.isNotEmpty()) delay(SEARCH_DELAY_MS)
        val results = when {
            text.isNotEmpty() -> search.bundled(text, ADD_CITY_LIMIT)
            purpose == CityPickPurpose.ADD -> search.popular(ADD_CITY_LIMIT, except = savedIds)
            else -> search.popular(ADD_CITY_LIMIT)
        }
        found = Found(text, results)
    }
    // Online search waits longer, for the typing to stop, and never starts with weather off.
    LaunchedEffect(text) {
        online = null
        if (!search.mayGoOnline(text)) return@LaunchedEffect
        val cached = search.cached(text)
        if (cached != null) {
            online = Online(text, OnlineSearch.Found(cached))
            return@LaunchedEffect
        }
        online = Online(text, OnlineSearch.Searching)
        delay(ONLINE_DELAY_MS)
        online = Online(text, search.online(text))
    }

    AddCityScreen(
        model = found?.let { results ->
            addCityModel(
                query = results.query,
                found = results.cities,
                savedIds = savedIds,
                now = now,
                phoneZone = phoneZone,
                online = online?.takeIf { it.query == results.query }?.result ?: OnlineSearch.Off,
                purpose = purpose,
                homeId = saved.firstOrNull { it.isHome }?.id,
            )
        },
        query = query,
        onQueryChange = { query = it },
        onAdd = { city ->
            scope.launch {
                cities.add(city)
                if (purpose == CityPickPurpose.HOME) cities.makeHome(city.id)
                onPicked(city)
                onBack()
            }
        },
        onBack = onBack,
        modifier = modifier,
        purpose = purpose,
    )
}

private val NoWeather = flowOf(WeatherState.Off)
private val InCelsius = flowOf(Units.CELSIUS)

private class Found(val query: String, val cities: List<City>)

private class Online(val query: String, val result: OnlineSearch)

private const val SEARCH_DELAY_MS = 150L

/** From the last key press, so it includes [SEARCH_DELAY_MS]. */
private const val ONLINE_DELAY_MS = 400L
