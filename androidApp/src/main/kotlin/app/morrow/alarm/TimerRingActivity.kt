package app.morrow.alarm

import android.os.Build
import android.os.Bundle
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
 * shows every ringing timer and closes once [app.morrow.timers.TimerCoordinator.ringing] empties: after Stop or
 * "+1 minute", at the safety stop, or when an alarm takes over.
 */
class TimerRingActivity : ComponentActivity() {

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

        val timers = morrow.timers
        setContent {
            WithTestFontScale {
                val ringing by timers.ringing.collectAsState()
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
                    val model = remember(ringing) { timerDoneModel(ringing, TimeZone.currentSystemDefault()) }
                    TimerDoneScreen(
                        model,
                        onStop = { lifecycleScope.launch { timers.stopRinging() } },
                        onAddMinute = { lifecycleScope.launch { timers.snoozeRinging() } },
                    )
                }
            }
        }
    }
}
