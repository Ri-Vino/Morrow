package app.morrow.ui.world

import androidx.compose.ui.graphics.Color
import app.morrow.theme.Illustration
import app.morrow.theme.contrastRatio

/** Text colour for words over a city's sky, and the even [wash] laid over the sky that keeps them readable. */
data class CardInk(val text: Color, val wash: Color)

/** Dark ink with a pale wash on a light sky, light ink with a dark wash otherwise, whichever reads better. */
fun inkFor(sky: Color): CardInk {
    val dark = contrastRatio(sky, Illustration.cityInkDark) >= contrastRatio(sky, Illustration.cityInkLight)
    return if (dark) {
        CardInk(Illustration.cityInkDark, Illustration.cityScrimLight.copy(alpha = 0.3f))
    } else {
        CardInk(Illustration.cityInkLight, Illustration.cityScrimDark.copy(alpha = 0.32f))
    }
}
