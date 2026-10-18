package app.morrow.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.morrow.alarm.RingService
import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.nextAlarm
import app.morrow.morrow
import app.morrow.platform.PermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.components.Pip
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.SkyScene
import app.morrow.weather.Feel
import app.morrow.weather.PlaceWeather
import app.morrow.weather.RefreshReason
import app.morrow.weather.WeatherAge
import app.morrow.weather.Warmth
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Developer tools: alarm engine, theme and scene. Home opens it for screens that aren't built yet. */
@Composable
fun DebugScreen(permissions: PermissionGate, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.morrow
    val scope = rememberCoroutineScope()
    val alarms by app.repository.alarms.collectAsState(initial = emptyList())
    val missing by permissions.missing.collectAsState()

    val zone = TimeZone.currentSystemDefault()
    val next = alarms.nextAlarm(Clock.System.now(), zone)

    val colors = MorrowTheme.colors
    val text = MorrowTheme.type.body.copy(color = colors.text)
    val themeClock = app.themeClock
    val pinned by themeClock.pinnedTheme.collectAsState()
    val theme by themeClock.theme.collectAsState()
    val minuteNow = Clock.System.now().toLocalDateTime(zone).let { it.hour * 60 + it.minute }
    // A pinned theme shows the design canvas's time for it; otherwise now, nudged by the hour buttons.
    var hourOffset by remember { mutableIntStateOf(0) }
    val sceneMinute = pinned?.let(::designMinute) ?: (minuteNow + hourOffset * 60).mod(24 * 60)
    val sceneTheme = pinned ?: ThemeKind.at(sceneMinute)
    var cardStates by rememberSaveable { mutableStateOf(false) }
    if (cardStates) {
        CityCardStates(onBack = { cardStates = false })
        return
    }

    var warmth by remember { mutableStateOf(Warmth.MILD) }
    var windy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) {
        SkyScene(sceneMinute.toFloat(), sceneTheme, feel = Feel(warmth, windy))
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                DebugButton("← Back", onClick = onBack)
                BasicText("Scene ${hhmm(sceneMinute / 60, sceneMinute % 60)} · ${theme.displayName}", style = text)
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                DebugButton("Auto", selected = pinned == null) { themeClock.pin(null) }
                for (kind in ThemeKind.entries) {
                    DebugButton(kind.name.lowercase().replaceFirstChar(Char::uppercase), selected = pinned == kind) {
                        themeClock.pin(kind)
                    }
                }
            }
            Row {
                DebugButton("−1 h") { hourOffset-- }
                DebugButton("Now", selected = hourOffset == 0) { hourOffset = 0 }
                DebugButton("+1 h") { hourOffset++ }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (kind in Warmth.entries) {
                    DebugButton(kind.name.lowercase().replaceFirstChar(Char::uppercase), selected = warmth == kind) { warmth = kind }
                }
                DebugButton("Windy", selected = windy) { windy = !windy }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (mood in PipMood.entries) {
                    Pip(PipLook(mood, rays = mood != PipMood.ASLEEP && mood != PipMood.YAWNING), Modifier.size(48.dp))
                }
                Pip(PipLook(PipMood.ASLEEP, moon = true, nightcap = true), Modifier.size(48.dp))
            }

            for (permission in missing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val need = if (permission.required) "Alarms can't ring properly" else "Recommended on this phone"
                    BasicText(
                        "$need: ${permission.name.lowercase().replace('_', ' ')}",
                        style = MorrowTheme.type.body.copy(color = colors.warn),
                        modifier = Modifier.weight(1f),
                    )
                    DebugButton("Allow") { permissions.request(permission) }
                }
            }

            BasicText(
                next?.let {
                    val at = it.at.toLocalDateTime(zone)
                    "Next: ${at.dayOfWeek.short()} ${hhmm(at.hour, at.minute)} (${it.alarm.label})"
                } ?: "No alarms on",
                style = text,
            )

            Row {
                DebugButton("Add alarm in 1 min") {
                    val inAMinute = (Clock.System.now() + 1.minutes).toLocalDateTime(zone)
                    val alarm = Alarm(0, inAMinute.hour, inAMinute.minute, "Test", emptySet(), DefaultGames, enabled = true)
                    scope.launch { app.coordinator.save(alarm) }
                }
                DebugButton("Ring now") { RingService.start(context, alarms.firstOrNull()?.id ?: -1) }
            }
            // Lets a Scan alarm be tried without a printed code.
            Row {
                DebugButton("Save kettle code TEST") { scope.launch { app.settings.setKettleCode("TEST") } }
                val extra by TestFontScale.extra.collectAsState()
                DebugButton("Text 200%", selected = extra != 1f) { TestFontScale.extra.value = if (extra == 1f) 2f else 1f }
            }

            WeatherPanel()
            DebugButton("City card states") { cardStates = true }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(alarms, key = Alarm::id) { alarm ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            "${hhmm(alarm.hour, alarm.minute)} · ${alarm.label} · ${alarm.days.describe()}",
                            style = text,
                            modifier = Modifier.weight(1f),
                        )
                        DebugButton(if (alarm.enabled) "On" else "Off") {
                            scope.launch { app.coordinator.setEnabled(alarm.id, !alarm.enabled) }
                        }
                        DebugButton("Delete") { scope.launch { app.coordinator.delete(alarm.id) } }
                    }
                }
            }
        }
    }
}

/** What the weather cache holds, so fetching can be checked on a phone before any screen draws weather. */
@Composable
private fun WeatherPanel() {
    val app = LocalContext.current.morrow
    val state by app.weather.forecasts.collectAsState(initial = null)
    val saved by app.cities.cities.collectAsState(initial = emptyList())
    val colors = MorrowTheme.colors
    val text = MorrowTheme.type.body.copy(color = colors.text)
    val weather = state
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            when {
                weather == null -> "Weather: loading"
                !weather.enabled -> "Weather: off"
                else -> weather.here?.let { "Weather here: ${it.name ?: it.coordinates.key} (${it.from.name.lowercase()})" } ?: "Weather here: unknown"
            },
            style = text,
            modifier = Modifier.weight(1f),
        )
        DebugButton("Refresh now") { app.weather.refreshSoon(RefreshReason.MANUAL) }
    }
    if (weather == null || !weather.enabled) return
    val places = listOfNotNull(weather.hereWeather?.let { "Here" to it }) +
        saved.mapNotNull { city -> weather.cities[city.id]?.let { city.city.name to it } }
    for ((name, place) in places) {
        BasicText("$name · ${place.place.key} · ${place.describe()}", style = MorrowTheme.type.caption.copy(color = colors.muted))
    }
}

private fun PlaceWeather.describe(): String {
    val forecast = forecast ?: return "nothing cached"
    val age = when (val age = ageAt(Clock.System.now())) {
        is WeatherAge.Fresh -> "${age.minutes} min old"
        is WeatherAge.Old -> "${age.hours} h old"
        WeatherAge.Expired -> "expired"
    }
    return "$age · ${"%.1f".format(forecast.now.temperature)} °C · ${forecast.now.sky.name.lowercase()}"
}

/** The times the design canvas uses for each theme's artboard. */
private fun designMinute(theme: ThemeKind): Int = when (theme) {
    ThemeKind.MORNING -> 7 * 60 + 40
    ThemeKind.NOON -> 12 * 60 + 30
    ThemeKind.AFTERNOON -> 16 * 60 + 45
    ThemeKind.NIGHT -> 21 * 60 + 40
}

private fun hhmm(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

private fun DayOfWeek.short() = name.take(3).lowercase().replaceFirstChar(Char::uppercase)

private fun Set<DayOfWeek>.describe() = when {
    isEmpty() -> "Once"
    size == 7 -> "Every day"
    else -> sorted().joinToString(" ") { it.short() }
}
