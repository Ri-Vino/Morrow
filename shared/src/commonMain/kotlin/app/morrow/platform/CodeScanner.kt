package app.morrow.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** Reads QR codes and barcodes with the camera, entirely on the phone. */
interface CodeScanner {
    val cameraAllowed: StateFlow<Boolean>

    /** Asks for the camera, or opens the settings screen where the user can allow it. */
    fun requestCamera()

    /** Re-reads the camera permission; call when the screen comes back to the foreground. */
    fun refresh()

    /** The live camera picture, calling [onCode] for each code it reads. The camera runs only while this is shown. */
    @Composable
    fun Viewfinder(onCode: (String) -> Unit, modifier: Modifier)
}
