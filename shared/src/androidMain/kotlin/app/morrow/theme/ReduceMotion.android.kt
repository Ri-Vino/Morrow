package app.morrow.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Android's Remove animations sets the animator duration scale to 0. */
@Composable
actual fun systemReducesMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return rememberResumedSetting { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
