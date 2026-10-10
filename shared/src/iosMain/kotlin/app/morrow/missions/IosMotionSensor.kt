@file:OptIn(ExperimentalForeignApi::class)

package app.morrow.missions

import app.morrow.platform.Acceleration
import app.morrow.platform.MotionSensor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

class IosMotionSensor : MotionSensor {

    private val motion = CMMotionManager()

    override val available: Boolean get() = motion.accelerometerAvailable

    override fun accelerations(): Flow<Acceleration> = callbackFlow {
        if (!motion.accelerometerAvailable) {
            close()
            return@callbackFlow
        }
        motion.accelerometerUpdateInterval = 1.0 / 50
        motion.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            if (data == null) return@startAccelerometerUpdatesToQueue
            val nanos = (data.timestamp * 1e9).toLong()
            // Core Motion measures in g, with the opposite sign to Android; the missions expect Android's m/s².
            data.acceleration.useContents {
                trySend(Acceleration((-x * GRAVITY).toFloat(), (-y * GRAVITY).toFloat(), (-z * GRAVITY).toFloat(), nanos))
            }
        }
        awaitClose { motion.stopAccelerometerUpdates() }
    }

    private companion object {
        const val GRAVITY = 9.80665
    }
}
