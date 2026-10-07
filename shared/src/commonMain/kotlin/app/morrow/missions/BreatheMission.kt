package app.morrow.missions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

const val BREATHS = 3

enum class BreatheNote { TOO_QUICK, TWO_MORE, ONE_MORE }

data class BreatheState(
    val breaths: Int,
    /** How far through the current breath, while Pip is held. */
    val hold: Float,
    val holding: Boolean,
    val note: BreatheNote?,
) : MissionState {
    override val progress: Float get() = (breaths + hold) / BREATHS
}

/** Hold Pip for one slow breath of [HOLD], three times. Letting go early starts that breath again. */
class BreatheMission(private val time: TimeSource = TimeSource.Monotonic) : Mission<BreatheState> {

    private val mutableState = MutableStateFlow(BreatheState(breaths = 0, hold = 0f, holding = false, note = null))
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
        val breaths = state.value.breaths + 1
        val note = when (breaths) {
            1 -> BreatheNote.TWO_MORE
            2 -> BreatheNote.ONE_MORE
            else -> null
        }
        mutableState.value = BreatheState(breaths = breaths, hold = 0f, holding = false, note = note)
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
