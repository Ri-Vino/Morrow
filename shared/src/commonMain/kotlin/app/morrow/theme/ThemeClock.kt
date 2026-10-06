package app.morrow.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The current theme. Instead of ticking, it waits once for the next theme boundary.
 * Call [refresh] when the app comes to the foreground or the time or timezone changes, and [pause] when it leaves.
 */
class ThemeClock(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val pinned = MutableStateFlow<ThemeKind?>(null)
    private var byClock = themeNow()
    private val current = MutableStateFlow(byClock)
    private var nextSwitch: Job? = null

    val theme: StateFlow<ThemeKind> = current.asStateFlow()
    val pinnedTheme: StateFlow<ThemeKind?> = pinned.asStateFlow()

    fun refresh() {
        nextSwitch?.cancel()
        byClock = themeNow()
        publish()
        val wait = untilNextBoundary()
        nextSwitch = scope.launch {
            delay(wait)
            refresh()
        }
    }

    fun pause() {
        nextSwitch?.cancel()
        nextSwitch = null
    }

    /** Keep one theme regardless of the time, or follow the clock again with null. */
    fun pin(kind: ThemeKind?) {
        pinned.value = kind
        publish()
    }

    private fun publish() {
        current.value = pinned.value ?: byClock
    }

    private fun themeNow(): ThemeKind {
        val local = clock.now().toLocalDateTime(timeZone())
        return ThemeKind.at(local.hour * 60 + local.minute)
    }

    private fun untilNextBoundary(): Duration {
        val now = clock.now()
        val zone = timeZone()
        val local = now.toLocalDateTime(zone)
        val next = ThemeKind.boundaries
            .map { LocalDateTime(local.date, LocalTime(it / 60, it % 60)) }
            .firstOrNull { it > local }
            ?: LocalDateTime(local.date + DatePeriod(days = 1), LocalTime(ThemeKind.boundaries.first() / 60, 0))
        // Guards against a boundary that lands in a daylight-saving overlap.
        return (next.toInstant(zone) - now).coerceAtLeast(1.seconds)
    }
}
