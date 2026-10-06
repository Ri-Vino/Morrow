package app.morrow.ui

import androidx.compose.runtime.Composable

/** iOS has no system back button; screens show their own back control. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
