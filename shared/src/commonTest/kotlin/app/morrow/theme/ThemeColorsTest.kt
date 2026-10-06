package app.morrow.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every pair that carries text must meet WCAG AA (4.5:1) in every theme. */
class ThemeColorsTest {

    @Test
    fun textPairsMeetContrast() {
        for (kind in ThemeKind.entries) {
            val c = kind.colors()
            val banner = mix(c.surface, c.warn, 0.12f)
            val pairs = mapOf(
                "text on bg" to (c.text to c.bg),
                "text on surface" to (c.text to c.surface),
                "text on surface2" to (c.text to c.surface2),
                "muted on bg" to (c.muted to c.bg),
                "muted on surface" to (c.muted to c.surface),
                "muted on surface2" to (c.muted to c.surface2),
                "hi on bg" to (c.hi to c.bg),
                "hi on surface" to (c.hi to c.surface),
                "hi on surface2" to (c.hi to c.surface2),
                "onAccent on accent" to (c.onAccent to c.accent),
                "onWarn on warn" to (c.onWarn to c.warn),
                "text on banner" to (c.text to banner),
                "muted on banner" to (c.muted to banner),
            )
            for ((name, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5f, "$kind $name is ${ratio}:1")
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
