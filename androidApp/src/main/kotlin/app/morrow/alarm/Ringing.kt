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
}
