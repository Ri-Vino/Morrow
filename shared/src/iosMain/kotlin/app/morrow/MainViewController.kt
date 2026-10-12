package app.morrow

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import app.morrow.ui.MorrowApp

fun MainViewController() = ComposeUIViewController {
    val waking by IosApp.waking.collectAsState()
    val wakingTimers by IosApp.wakingTimers.collectAsState()
    MorrowApp(
        services = IosApp.services,
        developerTools = null,
        // The status bar keeps the system's style for now.
        onSystemBarsChange = {},
        waking = waking,
        onWoken = IosApp::woken,
        wakingTimers = wakingTimers,
        onTimersWoken = IosApp::timersWoken,
    )
}
