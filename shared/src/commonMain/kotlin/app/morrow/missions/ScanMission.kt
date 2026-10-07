package app.morrow.missions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScanState(
    /** The code to find. With none saved yet, the first code scanned becomes it. */
    val savedCode: String?,
    val found: Boolean,
    /** Codes scanned that weren't the saved one. */
    val wrong: Int,
) : MissionState {
    override val progress: Float get() = if (found) 1f else 0f
}

/** Find the code saved on the kettle and scan it. */
class ScanMission(savedCode: String?) : Mission<ScanState> {

    private val mutableState = MutableStateFlow(ScanState(savedCode, found = false, wrong = 0))
    override val state: StateFlow<ScanState> = mutableState.asStateFlow()

    fun scanned(code: String) = mutableState.update {
        when {
            it.found -> it
            it.savedCode == null -> it.copy(savedCode = code, found = true)
            code == it.savedCode -> it.copy(found = true)
            else -> it.copy(wrong = it.wrong + 1)
        }
    }
}
