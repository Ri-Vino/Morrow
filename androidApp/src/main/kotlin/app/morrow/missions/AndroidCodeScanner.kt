package app.morrow.missions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.morrow.platform.CodeScanner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/** Create in the activity's onCreate: it registers for the camera permission result. */
class AndroidCodeScanner(private val activity: ComponentActivity) : CodeScanner {

    private val state = MutableStateFlow(allowed())
    override val cameraAllowed: StateFlow<Boolean> = state.asStateFlow()

    private val cameraRequest = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Once the user has said no for good, Android stops asking, so open the app's settings instead.
        if (!granted && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) openAppSettings()
        refresh()
    }

    override fun requestCamera() = cameraRequest.launch(Manifest.permission.CAMERA)

    override fun refresh() {
        state.value = allowed()
    }

    @Composable
    override fun Viewfinder(onCode: (String) -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val reportCode by rememberUpdatedState(onCode)
        var surface by remember { mutableStateOf<SurfaceRequest?>(null) }

        LaunchedEffect(lifecycleOwner) {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().apply { setSurfaceProvider { surface = it } }
            val analyser = Executors.newSingleThreadExecutor()
            val reader = CodeReader()
            val main = ContextCompat.getMainExecutor(context)
            var last: String? = null
            var lastAt = 0L
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analyser) { frame ->
                        val code = frame.use(reader::read) ?: return@setAnalyzer
                        main.execute {
                            // The same code shows up in frame after frame; report it once, or again after a pause.
                            val now = SystemClock.elapsedRealtime()
                            if (code != last || now - lastAt > REPEAT_AFTER_MS) {
                                last = code
                                lastAt = now
                                reportCode(code)
                            }
                        }
                    }
                }
            try {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                awaitCancellation()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "No back camera", e)
            } finally {
                provider.unbind(preview, analysis)
                analysis.clearAnalyzer()
                analyser.shutdown()
            }
        }

        // Embedded draws in order with the rest of the screen, so the window's rounded corners and overlays apply.
        surface?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier, implementationMode = ImplementationMode.EMBEDDED) }
    }

    private fun allowed() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    private companion object {
        const val TAG = "CodeScanner"
        const val REPEAT_AFTER_MS = 2_000L
    }
}
