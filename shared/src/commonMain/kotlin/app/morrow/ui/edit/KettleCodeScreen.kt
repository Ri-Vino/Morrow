package app.morrow.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.morrow.platform.CodeScanner
import app.morrow.theme.MorrowTheme
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.mission.ScanWindow
import kotlinx.coroutines.delay

/** Saves the code the Scan mission will ask for. Shown the first time Scan is picked for an alarm. */
@Composable
fun KettleCodeScreen(scanner: CodeScanner, onSaved: (String) -> Unit, onCancel: () -> Unit) {
    val colors = MorrowTheme.colors
    val cameraAllowed by scanner.cameraAllowed.collectAsState()
    var code by remember { mutableStateOf<String?>(null) }
    PlatformBackHandler(onBack = onCancel)
    LifecycleResumeEffect(scanner) {
        scanner.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(code) {
        val saved = code ?: return@LaunchedEffect
        delay(900)
        onSaved(saved)
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                "Pick your code",
                style = MorrowTheme.type.title.copy(color = colors.text),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            BasicText(
                "Any QR code or barcode works, even one cut from a packet. Stick it somewhere you have to walk to, like your kettle. " +
                    "To stop a Scan alarm, you’ll walk over and scan it.",
                style = MorrowTheme.type.bodyLarge.copy(color = colors.muted),
            )
            if (cameraAllowed) {
                ScanWindow(scanner, found = code != null, wrong = 0, onCode = { if (code == null) code = it })
            }
            BasicText(
                when {
                    code != null -> "Saved. That’s your code."
                    cameraAllowed -> "Point the camera at the code."
                    else -> "Pip needs the camera to read the code. It’s only used while scanning, and never saves a picture."
                },
                style = MorrowTheme.type.body.copy(color = colors.text, textAlign = TextAlign.Center),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(
            modifier = Modifier.navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!cameraAllowed) PrimaryButton("Allow camera", scanner::requestCamera)
            SecondaryButton("Not now", onCancel)
        }
    }
}
