package app.morrow.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.morrow.resources.Res
import app.morrow.resources.figtree
import app.morrow.resources.fraunces
import org.jetbrains.compose.resources.Font

/**
 * Figtree for UI text at 12, 13, 16 and 17; Fraunces for display text and numbers at 24, 40, 56 and 104.
 * Two weights only: 400 and 600. Text colour comes from [MorrowColors], not from these styles.
 *
 * Everything grows with the phone's font size, but the display sizes only up to [DISPLAY_SCALE_LIMIT] times: they're
 * big already, and a 104 sp time at twice the size no longer fits across the screen.
 */
@Immutable
data class MorrowType(
    /** Small uppercase section labels; uppercase the string itself. */
    val label: TextStyle,
    val caption: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodyLarge: TextStyle,
    val button: TextStyle,
    val title: TextStyle,
    val display: TextStyle,
    val displayLarge: TextStyle,
    val hero: TextStyle,
)

@Composable
internal fun morrowType(): MorrowType {
    val fontScale = LocalDensity.current.fontScale
    val figtree = FontFamily(
        Font(Res.font.figtree, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(Res.font.figtree, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )

    // Fraunces is set at its optical size for each display size. Its weight of 600 and fully soft shapes, as in the
    // design, are built into the file by tools/trim_fraunces.py.
    @Composable
    fun fraunces(size: Int) = FontFamily(
        Font(
            Res.font.fraunces,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.Setting("opsz", size.toFloat())),
        ),
    )

    fun sans(size: Int, weight: FontWeight, lineHeight: Float = 1.45f) =
        TextStyle(fontFamily = figtree, fontWeight = weight, fontSize = size.sp, lineHeight = (size * lineHeight).sp)

    fun serif(family: FontFamily, size: Int, tracking: Float, capped: Boolean = true): TextStyle {
        // Sizes in sp are multiplied by the font scale, so dividing it back out past the limit holds them there.
        val shown = if (capped && fontScale > DISPLAY_SCALE_LIMIT) size * DISPLAY_SCALE_LIMIT / fontScale else size.toFloat()
        return TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = shown.sp, lineHeight = (shown * 1.05f).sp, letterSpacing = tracking.em)
    }

    return MorrowType(
        label = sans(12, FontWeight.SemiBold).copy(letterSpacing = 0.14.em),
        caption = sans(13, FontWeight.Normal),
        body = sans(16, FontWeight.Normal),
        bodyStrong = sans(16, FontWeight.SemiBold),
        bodyLarge = sans(17, FontWeight.Normal, lineHeight = 1.5f),
        button = sans(16, FontWeight.SemiBold, lineHeight = 1.25f),
        // Titles are headings people read, so they keep growing.
        title = serif(fraunces(24), 24, tracking = 0f, capped = false),
        display = serif(fraunces(40), 40, tracking = -0.02f),
        displayLarge = serif(fraunces(56), 56, tracking = -0.03f),
        hero = serif(fraunces(104), 104, tracking = -0.04f),
    )
}

/** How far the display sizes grow with the phone's font size: to 1.3 times, where body text goes on to 2 times. */
internal const val DISPLAY_SCALE_LIMIT = 1.3f
