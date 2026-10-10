package app.morrow.missions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class BreatheNote { TOO_QUICK, MORE, TWO_MORE, ONE_MORE }

data class BreatheState(
    val breaths: Int,
    /** How far through the current breath, while Pip is held. */
    val hold: Float,
    val holding: Boolean,
    val note: BreatheNote?,
    /** Breaths needed to finish. */
    val count: Int = 3,
) : MissionState {
    override val progress: Float get() = (breaths + hold) / count

    val left: Int get() = count - breaths
}

/** Hold Pip for one slow breath of [HOLD], [count] times. Letting go early starts that breath again. */
class BreatheMission(count: Int = 3, private val time: TimeSource = TimeSource.Monotonic) : Mission<BreatheState> {

    private val mutableState = MutableStateFlow(BreatheState(breaths = 0, hold = 0f, holding = false, note = null, count = count))
    override val state: StateFlow<BreatheState> = mutableState.asStateFlow()
    private var pressedAt: TimeMark? = null

    fun press() {
        if (state.value.done || pressedAt != null) return
        pressedAt = time.markNow()
        mutableState.value = state.value.copy(holding = true, note = null)
    }

    /** Moves the current breath on; call it every frame while Pip is held. */
    fun tick() {
        val mark = pressedAt ?: return
        val hold = (mark.elapsedNow() / HOLD).toFloat()
        if (hold < 1f) {
            mutableState.value = state.value.copy(hold = hold)
            return
        }
        pressedAt = null
        val breathed = state.value.copy(breaths = state.value.breaths + 1, hold = 0f, holding = false)
        val note = when (breathed.left) {
            0 -> null
            1 -> BreatheNote.ONE_MORE
            2 -> BreatheNote.TWO_MORE
            else -> BreatheNote.MORE
        }
        mutableState.value = breathed.copy(note = note)
    }

    fun release() {
        tick()
        if (pressedAt == null) return
        pressedAt = null
        mutableState.value = state.value.copy(hold = 0f, holding = false, note = BreatheNote.TOO_QUICK)
    }

    companion object {
        val HOLD = 4.seconds
    }
}
