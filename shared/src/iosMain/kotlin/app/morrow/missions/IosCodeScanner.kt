@file:OptIn(ExperimentalForeignApi::class)

package app.morrow.missions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.morrow.permissions.openAppSettings
import app.morrow.platform.CodeScanner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeDataMatrixCode
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeInterleaved2of5Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/** Reads codes with AVFoundation, on the phone, the same kinds the Android reader does. */
class IosCodeScanner : CodeScanner {

    private val allowed = MutableStateFlow(isAllowed())
    override val cameraAllowed: StateFlow<Boolean> = allowed.asStateFlow()

    override fun requestCamera() {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) { allowed.value = granted }
            }
        } else {
            openAppSettings()
        }
    }

    override fun refresh() {
        allowed.value = isAllowed()
    }

    @Composable
    override fun Viewfinder(onCode: (String) -> Unit, modifier: Modifier) {
        val latest by rememberUpdatedState(onCode)
        val camera = remember { CameraView { latest(it) } }
        DisposableEffect(camera) {
            camera.start()
            onDispose { camera.stop() }
        }
        UIKitView(factory = { camera }, modifier = modifier)
    }

    private fun isAllowed() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
}

/** The back camera's picture, reporting each code it sees. The camera runs only between [start] and [stop]. */
private class CameraView(onCode: (String) -> Unit) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val session = AVCaptureSession()
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }
    // Kept here because the output only holds its delegate weakly.
    private val reader = CodeReader(onCode)
    // Starting and stopping the session block, so they run off the main thread, in order.
    private val queue = dispatch_queue_create("app.morrow.camera", null)

    init {
        layer.addSublayer(preview)
        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = camera?.let { AVCaptureDeviceInput(device = it, error = null) }
        if (input != null && session.canAddInput(input)) session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(reader, dispatch_get_main_queue())
            output.metadataObjectTypes = CodeTypes.filter { it in output.availableMetadataObjectTypes }
        }
    }

    fun start() = dispatch_async(queue) { session.startRunning() }

    fun stop() = dispatch_async(queue) { session.stopRunning() }

    override fun layoutSubviews() {
        super.layoutSubviews()
        preview.frame = bounds
    }
}

private class CodeReader(private val onCode: (String) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { it.stringValue }
            ?.let(onCode)
    }
}

// iOS reads UPC-A as EAN-13 with a leading zero.
private val CodeTypes = listOf(
    AVMetadataObjectTypeQRCode, AVMetadataObjectTypeDataMatrixCode, AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code,
    AVMetadataObjectTypeUPCECode, AVMetadataObjectTypeCode128Code, AVMetadataObjectTypeCode39Code,
    AVMetadataObjectTypeITF14Code, AVMetadataObjectTypeInterleaved2of5Code,
)
