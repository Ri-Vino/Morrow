package app.morrow.platform

import kotlinx.coroutines.flow.Flow

/** One accelerometer reading in m/s², gravity included. */
data class Acceleration(val x: Float, val y: Float, val z: Float, val timeNanos: Long)

interface MotionSensor {
    /** False on the rare phone without an accelerometer. */
    val available: Boolean

    /** Readings while collected. The sensor is switched on when collection starts and off the moment it stops. */
    fun accelerations(): Flow<Acceleration>
}
