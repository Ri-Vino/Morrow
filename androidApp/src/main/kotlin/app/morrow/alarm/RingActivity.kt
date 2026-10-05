package app.morrow.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morrow.debug.DebugButton

/** Shown over the lock screen while an alarm rings. Phase 7 replaces this debug content with the sunrise. */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Leaving the screen must not be a way to silence the alarm.
        onBackPressedDispatcher.addCallback(this) {}

        setContent {
            val ringing by Ringing.current.collectAsState()
            LaunchedEffect(ringing) { if (ringing == null) finish() }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                BasicText(ringing?.time.orEmpty(), style = TextStyle(fontSize = 56.sp))
                BasicText(ringing?.label.orEmpty(), style = TextStyle(fontSize = 16.sp))
                DebugButton("Stop") { RingService.stop(this@RingActivity) }
            }
        }
    }
}
