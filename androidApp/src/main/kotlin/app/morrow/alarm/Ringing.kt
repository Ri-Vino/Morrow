package app.morrow.alarm

import app.morrow.domain.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The alarm RingService is ringing right now, if any. RingActivity closes when this becomes null. */
object Ringing {
    private val state = MutableStateFlow<Alarm?>(null)
    val current: StateFlow<Alarm?> = state.asStateFlow()

    internal fun set(alarm: Alarm?) {
        state.value = alarm
    }
}
