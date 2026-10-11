package app.morrow.ui.world

import androidx.compose.ui.graphics.Color
import app.morrow.theme.Illustration
import app.morrow.theme.contrastRatio

/**
 * Text colour for words over a city's sky, and the wash laid under them.
 * The wash is strongest at the card's left and right edges and clear from [SCRIM_CLEAR_AT] of the width back to the middle,
 * so it keeps the name and clock readable without dulling the scene.
 */
data class CardInk(
    val text: Color,
    /** Behind the Home chip. */
    val chip: Color,
    val scrim: Color,
    val scrimLeft: Float,
    val scrimRight: Float,
) {
    /** How opaque the wash is [fraction] of the way across the card. */
    fun scrimAlphaAt(fraction: Float): Float = when {
        fraction <= SCRIM_CLEAR_AT -> scrimLeft * (1f - fraction / SCRIM_CLEAR_AT)
        else -> scrimRight * (fraction - SCRIM_CLEAR_AT) / (1f - SCRIM_CLEAR_AT)
    }
}

internal const val SCRIM_CLEAR_AT = 0.55f

/** Dark ink with a pale wash on a light sky, light ink with a dark wash otherwise, whichever reads better. */
fun inkFor(sky: Color): CardInk {
    val dark = contrastRatio(sky, Illustration.cityInkDark) >= contrastRatio(sky, Illustration.cityInkLight)
    return if (dark) {
        CardInk(Illustration.cityInkDark, Illustration.cityScrimLight.copy(alpha = 0.6f), Illustration.cityScrimLight, 0.3f, 0.22f)
    } else {
        CardInk(Illustration.cityInkLight, Illustration.cityScrimDark.copy(alpha = 0.38f), Illustration.cityScrimDark, 0.32f, 0.26f)
    }
}
