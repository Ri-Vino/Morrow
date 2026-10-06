package app.morrow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.morrow.debug.DebugScreen
import app.morrow.permissions.AndroidPermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.MorrowApp
import app.morrow.ui.MorrowServices
import app.morrow.ui.PendingScreens
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissions: AndroidPermissionGate

    private val clockChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = morrow.themeClock.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        permissions = AndroidPermissionGate(this)
        // Force-stopping the app clears its alarms without telling it, so restore them on every launch.
        lifecycleScope.launch { morrow.coordinator.rescheduleAll() }

        val app = morrow
        val services = MorrowServices(app.repository, app.coordinator, app.themeClock, app.settings, permissions)
        setContent {
            var tools by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = tools) { tools = false }
            if (tools) {
                val theme by app.themeClock.theme.collectAsState()
                MorrowTheme(theme) { DebugScreen(permissions, onBack = { tools = false }) }
                matchSystemBars(lightIcons = theme == ThemeKind.NIGHT)
            } else {
                val openTools = { tools = true }
                MorrowApp(
                    services = services,
                    pending = PendingScreens(trySunrise = openTools, preview = { openTools() }, settings = openTools),
                    onSystemBarsChange = ::matchSystemBars,
                )
            }
        }
    }

    /** Light status-bar icons over dark backgrounds, dark ones over light. */
    private fun matchSystemBars(lightIcons: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (lightIcons) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onStart() {
        super.onStart()
        morrow.themeClock.refresh()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(clockChanged, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(clockChanged, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just come back from a settings screen.
        permissions.refresh()
    }

    override fun onStop() {
        unregisterReceiver(clockChanged)
        morrow.themeClock.pause()
        super.onStop()
    }
}
