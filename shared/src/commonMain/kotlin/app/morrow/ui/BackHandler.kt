package app.morrow.ui

import androidx.compose.runtime.Composable

/** Runs [onBack] when the system back gesture or button is used, while [enabled]. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
