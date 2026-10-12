package app.morrow.ui.timers

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The current time, then again at the start of every second for as long as [stillCounting] says a timer is still
 * counting down at the time just emitted. The last time emitted is the first at which none is, so a card can show that
 * its timer has ended. Collect it only while the tab is visible.
 */
internal fun secondTicks(clock: Clock = Clock.System, stillCounting: (Instant) -> Boolean): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        if (!stillCounting(now)) break
        delay(1000 - now.toEpochMilliseconds() % 1000)
    }
}
