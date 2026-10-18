package app.morrow.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** The sky colour behind Pip, continuous through the day. Keyframes come from the design canvas. */
object SkyCurve {
    private val keyframes = listOf(
        0 to Color(0xFF0F1630),
        300 to Color(0xFF1C1F3C),
        350 to Color(0xFF5C2F48),
        390 to Color(0xFFE0907A),
        430 to Color(0xFFBFDDF2),
        540 to Color(0xFFA9D3F2),
        640 to Color(0xFFBFDCEB),
        720 to Color(0xFFF9DFA6),
        840 to Color(0xFFF8D394),
        960 to Color(0xFFF7C995),
        1060 to Color(0xFFF2A47A),
        1110 to Color(0xFFD8735E),
        1160 to Color(0xFF7A3A50),
        1230 to Color(0xFF20213F),
        1440 to Color(0xFF0F1630),
    )

    fun at(minuteOfDay: Float): Color {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val upper = keyframes.indexOfFirst { it.first >= m }.coerceAtLeast(1)
        val (fromMinute, from) = keyframes[upper - 1]
        val (toMinute, to) = keyframes[upper]
        return mix(from, to, (m - fromMinute) / (toMinute - fromMinute))
    }
}

/** A city card's rain turns dark once its sky is this bright. Home picks its ink by contrast instead. */
fun Color.isLightSky(): Boolean = luminance() > 0.3f

/** -1 at midnight, 0 at 06:00 and 18:00, 1 at noon. */
fun sunElevation(minuteOfDay: Float): Double = -cos(2 * PI * minuteOfDay / MINUTES_PER_DAY)

/** Linear mix in sRGB, rounded to whole channels, matching the design's colour maths. */
fun mix(a: Color, b: Color, t: Float): Color {
    fun channel(x: Float, y: Float) = ((x + (y - x) * t) * 255).roundToInt().coerceIn(0, 255)
    return Color(channel(a.red, b.red), channel(a.green, b.green), channel(a.blue, b.blue))
}

private const val MINUTES_PER_DAY = 24 * 60f
