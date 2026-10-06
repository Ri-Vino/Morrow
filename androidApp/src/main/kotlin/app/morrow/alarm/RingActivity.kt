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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morrow.debug.DebugButton
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind

/** Shown over the lock screen while an alarm rings. Phase 7 replaces this debug content with the sunrise. */
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
            val ringing by Ringing.current.collectAsState()
            LaunchedEffect(ringing) { if (ringing == null) finish() }

            MorrowTheme(ThemeKind.NIGHT) { RingDebugContent(ringing) }
        }
    }

    @Composable
    private fun RingDebugContent(ringing: RingingAlarm?) {
        val colors = MorrowTheme.colors
        Column(
            modifier = Modifier.fillMaxSize().background(colors.bg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            BasicText(ringing?.time.orEmpty(), style = MorrowTheme.type.hero.copy(color = colors.text))
            BasicText(ringing?.label.orEmpty(), style = MorrowTheme.type.bodyLarge.copy(color = colors.muted))
            DebugButton("Stop") { RingService.stop(this@RingActivity) }
        }
    }
}
