package app.morrow.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.morrow.ui.ring.RingScreen
import app.morrow.ui.ring.ringModel

/** Shown over the lock screen while an alarm rings. */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Leaving the screen must not be a way to silence the alarm.
        onBackPressedDispatcher.addCallback(this) {}

        setContent {
            val alarm by Ringing.current.collectAsState()
            LaunchedEffect(alarm) { if (alarm == null) finish() }
            // Until Phase 8 adds missions, starting one stops the alarm.
            alarm?.let { RingScreen(ringModel(it), onStartMission = { RingService.stop(this) }) }
        }
    }
}
