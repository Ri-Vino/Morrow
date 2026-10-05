package app.morrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import app.morrow.debug.DebugScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val resumeCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Force-stopping the app clears its alarms without telling it, so restore them on every launch.
        lifecycleScope.launch { morrow.coordinator.rescheduleAll() }
        setContent { DebugScreen(resumeCount.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
    }
}
