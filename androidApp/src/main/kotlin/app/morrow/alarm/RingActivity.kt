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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.morrow.missions.AndroidCodeScanner
import app.morrow.missions.AndroidMotionSensor
import app.morrow.morrow
import app.morrow.ui.mission.MissionRoute
import app.morrow.ui.mission.MissionServices
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

        val missions = MissionServices(morrow.settings, AndroidMotionSensor(this), AndroidCodeScanner(this))
        setContent {
            val alarm by Ringing.current.collectAsState()
            var inMission by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(alarm) { if (alarm == null) finish() }
            val ringing = alarm ?: return@setContent
            if (inMission) {
                // The alarm rings on until the mission is done. If this screen fails, the safety stop still ends it.
                MissionRoute(ringing.mission, missions, onDone = { RingService.stop(this) })
            } else {
                RingScreen(ringModel(ringing), onStartMission = { inMission = true })
            }
        }
    }
}
