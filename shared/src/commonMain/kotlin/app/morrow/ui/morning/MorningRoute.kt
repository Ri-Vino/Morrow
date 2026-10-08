package app.morrow.ui.morning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.morrow.data.MorrowSettings
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The Morning screen for a mission finished at [wokeAt]. [onStartDay] gets what the user typed, trimmed. */
@Composable
fun MorningRoute(settings: MorrowSettings, wokeAt: Instant, onStartDay: (intention: String) -> Unit) {
    val wentToBed by remember(settings) { settings.lastClosedAt }.collectAsState(initial = null)
    MorningScreen(morningModel(wokeAt, wentToBed, TimeZone.currentSystemDefault()), onStartDay)
}
