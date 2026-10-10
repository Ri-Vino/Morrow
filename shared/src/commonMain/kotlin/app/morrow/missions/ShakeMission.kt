package app.morrow.missions

import app.morrow.platform.Acceleration
import app.morrow.platform.MotionSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class ShakeState(val shakes: Int, val goal: Int) : MissionState {
    override val progress: Float get() = shakes.toFloat() / goal
}

class ShakeMission(goal: Int, private val detector: ShakeDetector = ShakeDetector()) : Mission<ShakeState> {

    private val mutableState = MutableStateFlow(ShakeState(shakes = 0, goal = goal))
    override val state: StateFlow<ShakeState> = mutableState.asStateFlow()

    /** Counts shakes from [sensor] until the goal is reached, and stops the sensor the moment it is. */
    suspend fun listen(sensor: MotionSensor) {
        if (state.value.done) return
        sensor.accelerations()
            .transformWhile { reading ->
                if (detector.isShake(reading)) mutableState.update { it.copy(shakes = minOf(it.goal, it.shakes + 1)) }
                emit(Unit)
                !state.value.done
            }
            .collect()
    }
}

/**
 * A shake is a jolt of at least [threshold] times gravity, which the difficulty sets. One jolt lasts several readings,
 * so shakes are at least [gap] apart.
 */
class ShakeDetector(private val threshold: Float = 2f, private val gap: Duration = 250.milliseconds) {

    private var lastShakeAt: Long? = null

    fun isShake(reading: Acceleration): Boolean {
        val force = sqrt(reading.x * reading.x + reading.y * reading.y + reading.z * reading.z) / GRAVITY
        if (force < threshold) return false
        val last = lastShakeAt
        if (last != null && reading.timeNanos - last < gap.inWholeNanoseconds) return false
        lastShakeAt = reading.timeNanos
        return true
    }

    private companion object {
        const val GRAVITY = 9.80665f
    }
}
