package app.morrow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.morrow.alarm.AlarmTone
import app.morrow.debug.DebugScreen
import app.morrow.debug.WithTestFontScale
import app.morrow.missions.AndroidCodeScanner
import app.morrow.missions.AndroidMotionSensor
import app.morrow.permissions.AndroidPermissionGate
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.MorrowApp
import app.morrow.ui.MorrowServices
import app.morrow.ui.mission.MissionServices
import app.morrow.weather.RefreshReason
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var permissions: AndroidPermissionGate

    private val clockChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = morrow.themeClock.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        permissions = AndroidPermissionGate(this)
        // Force-stopping the app clears its alarms and timers without telling it, so restore them on every launch.
        lifecycleScope.launch {
            morrow.coordinator.rescheduleAll()
            morrow.timers.rescheduleAll()
        }

        val app = morrow
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val missions = MissionServices(app.settings, AndroidMotionSensor(this), AndroidCodeScanner(this))
        val services = MorrowServices(
            app.repository, app.coordinator, app.themeClock, app.settings, permissions, AlarmTone(app), missions,
            app.cities, app.catalog, app.citySearch, app.weather, phoneCountry = Locale.getDefault().country, speaker = app.speaker,
            timers = app.timers,
            canSnooze = true,
        )
        setContent {
            WithTestFontScale {
                var tools by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = tools) { tools = false }
                if (tools) {
                    val theme by app.themeClock.theme.collectAsState()
                    MorrowTheme(theme) { DebugScreen(permissions, onBack = { tools = false }) }
                    matchSystemBars(lightIcons = theme == ThemeKind.NIGHT || isSystemInDarkTheme())
                } else {
                    MorrowApp(
                        services = services,
                        developerTools = if (debuggable) ({ tools = true }) else null,
                        onSystemBarsChange = ::matchSystemBars,
                    )
                }
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
        // Weather is only fetched with the app open: here, on opening World, and on the Morning screen.
        morrow.weather.refreshSoon(RefreshReason.START)
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
        // Turning the phone isn't leaving the app, and needn't write to disk.
        if (!isChangingConfigurations) morrow.markClosed()
        super.onStop()
    }
}
