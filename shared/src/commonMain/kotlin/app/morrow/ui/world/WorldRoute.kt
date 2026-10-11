package app.morrow.ui.world

import androidx.compose.runtime.Composable
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
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.places.SavedCity
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.minuteTicks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** The World tab over the saved cities. [ticks] is the time, then each minute after; the clock's by default. */
@Composable
fun WorldRoute(
    cities: CityRepository,
    onAddCity: () -> Unit,
    modifier: Modifier = Modifier,
    /** A new alarm on a city's clock, or on the phone's when the city keeps the same time. */
    onNewAlarm: (City?) -> Unit = {},
    ticks: Flow<Instant>? = null,
    phoneZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val scope = rememberCoroutineScope()
    val saved by cities.cities.collectAsStateWithLifecycle<List<SavedCity>?>(initialValue = null)
    val now by remember(ticks) { ticks ?: minuteTicks() }.collectAsStateWithLifecycle(initialValue = Clock.System.now())
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }

    val list = saved ?: return
    val open = openId?.let { id -> list.firstOrNull { it.id == id } }
    WorldScreen(
        model = worldModel(list, now, phoneZone),
        sheet = open?.let { citySheet(it, now, phoneZone) },
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

/** Add a city: search the bundled list, and tapping + saves the city and goes [onBack] to World. */
@Composable
fun AddCityRoute(
    catalog: CityCatalog,
    cities: CityRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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

    // The list follows the typing a moment behind, so a fast typist doesn't search for every letter.
    val text = query.trim()
    LaunchedEffect(text, savedIds) {
        if (text.isNotEmpty()) delay(SEARCH_DELAY_MS)
        val results = if (text.isEmpty()) catalog.mostPopulous(ADD_CITY_LIMIT, except = savedIds) else catalog.search(text, ADD_CITY_LIMIT)
        found = Found(text, results)
    }

    AddCityScreen(
        model = found?.let { addCityModel(it.query, it.cities, savedIds, now, phoneZone) },
        query = query,
        onQueryChange = { query = it },
        onAdd = { city ->
            scope.launch {
                cities.add(city)
                onBack()
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

private class Found(val query: String, val cities: List<City>)

private const val SEARCH_DELAY_MS = 150L
