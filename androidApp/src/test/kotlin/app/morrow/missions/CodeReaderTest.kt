package app.morrow.missions

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeReaderTest {

    private val reader = CodeReader()

    /** A camera-like frame: the code drawn dark on light, with rows padded past the image's width. */
    private class Frame(val bytes: ByteArray, val rowStride: Int, val width: Int, val height: Int)

    private fun frame(code: BitMatrix, padding: Int = 16): Frame {
        val rowStride = code.width + padding
        val bytes = ByteArray(rowStride * code.height) { 0xE0.toByte() }
        for (y in 0 until code.height) for (x in 0 until code.width) {
            if (code[x, y]) bytes[y * rowStride + x] = 0x20
        }
        return Frame(bytes, rowStride, code.width, code.height)
    }

    /** [code] turned a quarter anticlockwise, as a phone held upright sees a horizontal barcode. */
    private fun sideways(code: BitMatrix): BitMatrix {
        val out = BitMatrix(code.height, code.width)
        for (y in 0 until code.height) for (x in 0 until code.width) {
            if (code[x, y]) out.set(y, code.width - 1 - x)
        }
        return out
    }

    private fun read(frame: Frame) = reader.read(frame.bytes, frame.rowStride, frame.width, frame.height)

    @Test
    fun readsAQrCode() {
        val code = MultiFormatWriter().encode("MORROW-KETTLE-1", BarcodeFormat.QR_CODE, 400, 400)
        assertEquals("MORROW-KETTLE-1", read(frame(code)))
    }

    @Test
    fun readsABarcodeEitherWayRound() {
        val code = MultiFormatWriter().encode("KETTLE42", BarcodeFormat.CODE_128, 480, 160)
        assertEquals("KETTLE42", read(frame(code)))
        assertEquals("KETTLE42", read(frame(sideways(code))))
    }

    @Test
    fun findsNothingInAPlainFrame() {
        assertNull(reader.read(ByteArray(640 * 480) { 0x80.toByte() }, 640, 640, 480))
    }
}
