package app.morrow.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RingingAlarm(val id: Long, val time: String, val label: String)

/** What RingService is ringing right now, if anything. RingActivity closes when this becomes null. */
object Ringing {
    private val state = MutableStateFlow<RingingAlarm?>(null)
    val current: StateFlow<RingingAlarm?> = state.asStateFlow()

    internal fun set(alarm: RingingAlarm?) {
        state.value = alarm
    }
}
