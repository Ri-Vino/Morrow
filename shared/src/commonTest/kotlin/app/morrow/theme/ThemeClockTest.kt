package app.morrow.theme

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val london = TimeZone.of("Europe/London")

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeClockTest {

    private var zone = london

    /** A clock that moves with the test's virtual time. */
    private fun TestScope.clockStartingAt(iso: String): Clock {
        val start = LocalDateTime.parse(iso).toInstant(london)
        return object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }
    }

    private fun TestScope.themeClock(startAt: String) = ThemeClock(backgroundScope, clockStartingAt(startAt)) { zone }

    private fun TestScope.advance(by: Duration) {
        advanceTimeBy(by)
        runCurrent()
    }

    @Test
    fun startsWithTheThemeForTheCurrentTime() = runTest {
        assertEquals(ThemeKind.MORNING, themeClock("2026-10-06T10:00").theme.value)
    }

    @Test
    fun switchesExactlyAtTheNextBoundary() = runTest {
        val clock = themeClock("2026-10-06T10:59:30")
        clock.refresh()

        advance(30.seconds - 1.milliseconds)
        assertEquals(ThemeKind.MORNING, clock.theme.value)

        advance(1.milliseconds)
        assertEquals(ThemeKind.NOON, clock.theme.value)
    }

    @Test
    fun keepsSwitchingThroughTheNight() = runTest {
        val clock = themeClock("2026-10-06T18:30")
        clock.refresh()
        assertEquals(ThemeKind.AFTERNOON, clock.theme.value)

        advance(30.minutes)
        assertEquals(ThemeKind.NIGHT, clock.theme.value)

        advance(10.hours)
        assertEquals(ThemeKind.MORNING, clock.theme.value)
    }

    @Test
    fun pausedClockStopsSwitchingUntilRefreshed() = runTest {
        val clock = themeClock("2026-10-06T10:59")
        clock.refresh()
        clock.pause()

        advance(5.minutes)
        assertEquals(ThemeKind.MORNING, clock.theme.value)

        clock.refresh()
        assertEquals(ThemeKind.NOON, clock.theme.value)
    }

    @Test
    fun pinnedThemeOverridesTheClock() = runTest {
        val clock = themeClock("2026-10-06T22:00")
        clock.refresh()

        clock.pin(ThemeKind.NOON)
        advance(8.hours)
        assertEquals(ThemeKind.NOON, clock.theme.value)

        clock.pin(null)
        assertEquals(ThemeKind.MORNING, clock.theme.value)
    }

    @Test
    fun timezoneChangeAppliesOnRefresh() = runTest {
        val clock = themeClock("2026-10-06T10:30")
        assertEquals(ThemeKind.MORNING, clock.theme.value)

        zone = TimeZone.of("Asia/Tokyo")
        clock.refresh()
        assertEquals(ThemeKind.AFTERNOON, clock.theme.value)
    }
}
