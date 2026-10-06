package app.morrow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.lifecycleScope
import app.morrow.debug.DebugScreen
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val resumeCount = mutableIntStateOf(0)

    private val clockChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = morrow.themeClock.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Force-stopping the app clears its alarms without telling it, so restore them on every launch.
        lifecycleScope.launch { morrow.coordinator.rescheduleAll() }
        setContent {
            val theme by morrow.themeClock.theme.collectAsState()
            LaunchedEffect(theme) { matchSystemBars(theme) }
            MorrowTheme(theme) { DebugScreen(resumeCount.intValue) }
        }
    }

    /** Light status-bar icons on dark themes, dark icons on light ones. */
    private fun matchSystemBars(theme: ThemeKind) {
        val style = if (theme.colors().bg.luminance() < 0.5f) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
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
        resumeCount.intValue++
    }

    override fun onStop() {
        unregisterReceiver(clockChanged)
        morrow.themeClock.pause()
        super.onStop()
    }
}
