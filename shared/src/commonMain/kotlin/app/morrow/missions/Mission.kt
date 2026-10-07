package app.morrow.missions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface MissionState {
    /** From 0 to 1. Pip rises with it, and the alarm stops only when it reaches 1. */
    val progress: Float

    val done: Boolean get() = progress >= 1f
}

/** A small task that proves you're awake. Input is ignored once it's done. */
interface Mission<S : MissionState> {
    val state: StateFlow<S>

    val progress: Flow<Float> get() = state.map { it.progress }.distinctUntilChanged()
}
