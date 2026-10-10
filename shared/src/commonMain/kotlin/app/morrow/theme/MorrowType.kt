package app.morrow.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
 * Figtree for UI text at 12, 14 and 16; Fraunces for display text and numbers at 24, 40, 56 and 104.
 * Two weights only: 400 and 600. Text colour comes from [MorrowColors], not from these styles.
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

    fun serif(family: FontFamily, size: Int, tracking: Float) =
        TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = (size * 1.05f).sp, letterSpacing = tracking.em)

    return MorrowType(
        label = sans(12, FontWeight.SemiBold).copy(letterSpacing = 0.14.em),
        caption = sans(12, FontWeight.Normal),
        body = sans(14, FontWeight.Normal),
        bodyStrong = sans(14, FontWeight.SemiBold),
        bodyLarge = sans(16, FontWeight.Normal, lineHeight = 1.5f),
        button = sans(16, FontWeight.SemiBold, lineHeight = 1.25f),
        title = serif(fraunces(24), 24, tracking = 0f),
        display = serif(fraunces(40), 40, tracking = -0.02f),
        displayLarge = serif(fraunces(56), 56, tracking = -0.03f),
        hero = serif(fraunces(104), 104, tracking = -0.04f),
    )
}
