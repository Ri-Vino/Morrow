package app.morrow.missions

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.morrow.platform.Acceleration
import app.morrow.platform.MotionSensor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidMotionSensor(context: Context) : MotionSensor {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val available: Boolean get() = accelerometer != null

    override fun accelerations(): Flow<Acceleration> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Acceleration(event.values[0], event.values[1], event.values[2], event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }
}
