package app.morrow.missions

import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Finds a QR code or barcode in a camera frame, using only the frame's brightness. */
internal class CodeReader {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.ITF,
                ),
            ),
        )
    }

    fun read(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return read(bytes, plane.rowStride, image.width, image.height)
    }

    /** Reads a frame's brightness, one byte per pixel, rows [rowStride] bytes apart. */
    fun read(bytes: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val upright = PlanarYUVLuminanceSource(bytes, rowStride, height, 0, 0, width, height, false)
        // Barcodes only read across their bars, and the camera's frames are sideways to a phone held upright.
        return decode(upright) ?: decode(turned(bytes, rowStride, width, height))
    }

    private fun decode(source: LuminanceSource): String? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    /** The frame turned a quarter clockwise. */
    private fun turned(bytes: ByteArray, rowStride: Int, width: Int, height: Int): LuminanceSource {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            val row = y * rowStride
            for (x in 0 until width) out[x * height + (height - 1 - y)] = bytes[row + x]
        }
        return PlanarYUVLuminanceSource(out, height, width, 0, 0, height, width, false)
    }
}
