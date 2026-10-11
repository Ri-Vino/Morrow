package app.morrow.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** The WCAG contrast ratio between two opaque colours, from 1 (identical) to 21 (black on white). */
fun contrastRatio(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (max(x, y) + 0.05f) / (min(x, y) + 0.05f)
}
