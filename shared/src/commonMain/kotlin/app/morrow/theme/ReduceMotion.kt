package app.morrow.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * True when the phone asks for less motion: Reduce Motion on iPhone, Remove animations on Android. Read again each
 * time the app comes back to the front, since it's changed in the phone's settings.
 */
@Composable
expect fun systemReducesMotion(): Boolean

/** [read] now, and again whenever the app is resumed. */
@Composable
internal fun rememberResumedSetting(read: () -> Boolean): Boolean {
    var value by remember { mutableStateOf(read()) }
    LifecycleResumeEffect(Unit) {
        value = read()
        onPauseOrDispose {}
    }
    return value
}
