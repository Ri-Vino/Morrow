package app.morrow.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.morrow.debug.WithTestFontScale
import app.morrow.morrow
import app.morrow.theme.Illustration
import app.morrow.ui.timers.TimerDoneScreen
import app.morrow.ui.timers.timerDoneModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds

/**
 * The Timer done screen over the lock screen, opened by RingService's full-screen notification while timers ring. It
 * shows every ringing timer, with Pip talking while RingService has him read their notes, and closes once
 * [app.morrow.timers.TimerCoordinator.ringing] empties: after Stop or "+1 minute", at the safety stop, or when an alarm
 * takes over.
 *
 * Opened by [TimerNotifications.ringWithoutService] instead, when RingService couldn't start from the background, it
 * starts RingService itself once it's in front, which is always allowed.
 */
class TimerRingActivity : ComponentActivity() {

    private var startRinging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(transparent), navigationBarStyle = SystemBarStyle.dark(transparent))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Stop and +1 minute are right there; leaving would only hide a chime that keeps going.
        onBackPressedDispatcher.addCallback(this) {}

        startRinging = intent.getBooleanExtra(EXTRA_START_RINGING, false)
        val timers = morrow.timers
        val voice = morrow.timerVoice
        setContent {
            WithTestFontScale {
                val ringing by timers.ringing.collectAsState()
                val speaking by voice.speaking.collectAsState()
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(ringing.isEmpty()) {
                    if (ringing.isNotEmpty()) {
                        shown = true
                    } else {
                        // The notification can open this a moment before RingService has found which timers ended.
                        if (!shown) delay(3.seconds)
                        finish()
                    }
                }
                if (ringing.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(Illustration.duskSky))
                } else {
                    val model = remember(ringing, speaking) { timerDoneModel(ringing, TimeZone.currentSystemDefault(), speaking) }
                    TimerDoneScreen(
                        model,
                        onStop = {
                            voice.hush()
                            lifecycleScope.launch { timers.stopRinging() }
                        },
                        onAddMinute = {
                            voice.hush()
                            lifecycleScope.launch { timers.snoozeRinging() }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_RINGING, false)) startRinging = true
    }

    override fun onStart() {
        super.onStart()
        if (!startRinging) return
        startRinging = false
        try {
            RingService.ringTimers(this)
            TimerNotifications.hideAlert(this)
        } catch (e: IllegalStateException) {
            // Its alert stays up, with its sound.
            Log.e(TAG, "Couldn't start ringing", e)
        }
    }

    companion object {
        /** Starts RingService, for when it couldn't start itself. */
        const val EXTRA_START_RINGING = "start_ringing"
        private const val TAG = "TimerRingActivity"
    }
}
