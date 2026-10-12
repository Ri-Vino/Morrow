package app.morrow.ui.timers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimerTicksTest {

    // A quarter of a second into a second, so the ticks are not on whole seconds to begin with.
    private val start = Instant.fromEpochMilliseconds(1_800_000_250)

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = start + testScheduler.currentTime.milliseconds
    }

    @Test
    fun tickAtTheStartOfEachSecondUntilNoTimerIsLeftCounting() = runTest {
        val endsAt = start + 2.seconds + 100.milliseconds

        val ticks = secondTicks(clock()) { at -> endsAt > at }.toList()

        // The last tick is the first after the end, so the card gets to say it's over.
        assertEquals(listOf(start, start + 750.milliseconds, start + 1750.milliseconds, start + 2750.milliseconds), ticks)
    }

    @Test
    fun withNothingCountingItGivesTheTimeOnceAndStops() = runTest {
        assertEquals(listOf(start), secondTicks(clock()) { false }.toList())
    }
}
