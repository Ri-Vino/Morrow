package app.morrow.alarm

import android.app.KeyguardManager
import android.content.Intent
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.morrow.MainActivity
import app.morrow.missions.AndroidCodeScanner
import app.morrow.missions.AndroidMotionSensor
import app.morrow.morrow
import app.morrow.ui.mission.MissionRoute
import app.morrow.ui.mission.MissionServices
import app.morrow.ui.morning.MorningRoute
import app.morrow.ui.ring.RingScreen
import app.morrow.ui.ring.ringModel
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** Shown over the lock screen from the moment an alarm rings until the Morning screen is done. */
class RingActivity : ComponentActivity() {

    private enum class Stage { RINGING, MISSION, MORNING }

    override fun onCreate(savedInstanceState: Bundle?) {
        matchSystemBars(lightIcons = true)
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
            var stage by rememberSaveable { mutableStateOf(Stage.RINGING) }
            var wokeAt by rememberSaveable { mutableLongStateOf(0L) }
            // The alarm stops when the mission is done, or at the safety stop; only the first leads to Morning.
            LaunchedEffect(alarm, stage) { if (alarm == null && stage != Stage.MORNING) finish() }
            LaunchedEffect(stage) { matchSystemBars(lightIcons = stage != Stage.MORNING) }

            val ringing = alarm
            when {
                stage == Stage.MORNING -> MorningRoute(morrow.settings, Instant.fromEpochMilliseconds(wokeAt), onStartDay = ::startDay)
                ringing == null -> Unit
                // The alarm rings on until the mission is done. If this screen fails, the safety stop still ends it.
                stage == Stage.MISSION -> MissionRoute(
                    ringing.mission,
                    missions,
                    onDone = {
                        wokeAt = Clock.System.now().toEpochMilliseconds()
                        stage = Stage.MORNING
                        RingService.stop(this)
                    },
                )
                else -> RingScreen(ringModel(ringing), onStartMission = { stage = Stage.MISSION })
            }
        }
    }

    /** Saves the answer, then opens Home, asking for the phone to be unlocked first if it's locked. */
    private fun startDay(intention: String) {
        lifecycleScope.launch {
            if (intention.isNotEmpty()) morrow.settings.setIntention(intention)
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (!keyguard.isKeyguardLocked) {
                openHome()
                return@launch
            }
            keyguard.requestDismissKeyguard(
                this@RingActivity,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = openHome()
                    override fun onDismissCancelled() = finish()
                    override fun onDismissError() = finish()
                },
            )
        }
    }

    /** Opens the app as its launcher icon would, bringing back its task rather than stacking a second Home. */
    private fun openHome() {
        val launch = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(launch)
        finish()
    }

    /** Light status-bar icons over the night sky, dark ones over the Morning screen. */
    private fun matchSystemBars(lightIcons: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (lightIcons) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
