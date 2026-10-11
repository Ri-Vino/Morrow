package app.morrow.platform

import app.morrow.weather.Coordinates
import kotlin.time.Duration

/** A coarse location for "Where I am" weather. Ask only while the app is open and weather is on. */
interface LocationProvider {
    /** Whether the user has allowed location. */
    val allowed: Boolean

    /**
     * A fix already rounded with [Coordinates.rounded], or null when location isn't allowed, is switched off,
     * or nothing arrives within [timeout].
     */
    suspend fun current(timeout: Duration): Coordinates?
}
