package app.morrow.alarm

import app.morrow.domain.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The alarm RingService is ringing right now, if any. RingActivity closes when this becomes null. */
object Ringing {
    private val state = MutableStateFlow<Alarm?>(null)
    val current: StateFlow<Alarm?> = state.asStateFlow()

    /**
     * True while RingActivity shows the Morning screen, after the alarm itself has stopped, for up to ten minutes.
     * Timers that end meanwhile wait for it to go, as they do while the alarm rings.
     */
    @Volatile
    var morningShowing = false
        internal set

    internal fun set(alarm: Alarm?) {
        state.value = alarm
    }

    /** How many times the ringing alarm has been snoozed on the way here. */
    @Volatile
    var snoozed = 0
        internal set

    private val level = MutableStateFlow(1f)

    /** How loud the alarm plays, as RingActivity sets it for the games: 1 as it rang, less while they're played, 0 muted. */
    val gameLevel: StateFlow<Float> = level.asStateFlow()

    fun setGameLevel(value: Float) {
        level.value = value
    }
}
