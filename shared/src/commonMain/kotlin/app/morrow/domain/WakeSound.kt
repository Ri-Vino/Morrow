package app.morrow.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The alarm's sound. All three are bundled with the app. */
enum class WakeSound(val displayName: String) {
    BIRDSONG("Birdsong"),
    WIND_CHIMES("Wind chimes"),
    SOFT_PIANO("Soft piano"),
}

/** How long the alarm takes to rise from quiet to full volume. */
enum class VolumeRamp(val displayName: String, val duration: Duration) {
    HALF_MINUTE("30 s", 30.seconds),
    ONE_MINUTE("1 min", 1.minutes),
    TWO_MINUTES("2 min", 2.minutes),
}
