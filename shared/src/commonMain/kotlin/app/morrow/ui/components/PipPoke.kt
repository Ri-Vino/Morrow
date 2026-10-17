package app.morrow.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import app.morrow.theme.ThemeKind
import kotlin.math.PI
import kotlin.math.sin

/** How Pip is moved at one moment of a poke, in the sky's design units and degrees, on top of where Pip already is. */
data class PokeFrame(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    /** The sun beams while it's being played with; the moon sleeps on. */
    val delighted: Boolean = false,
    /** How much brighter the stars twinkle, 0 to 1. */
    val starBoost: Float = 0f,
)

/**
 * Pip's answer to a tap, [progress] from 0 to 1 through it, which differs with the time of day: in the morning a
 * stretchy hop, at noon a spin in its shades, in the afternoon a happy wiggle, and the [moon] rocks in its sleep
 * while the stars twinkle. At 0 and 1 Pip is where it was.
 */
fun pipPoke(theme: ThemeKind, moon: Boolean, progress: Float): PokeFrame {
    val p = progress.coerceIn(0f, 1f)
    if (p == 0f || p == 1f) return PokeFrame()
    val arc = sin(PI.toFloat() * p)
    val fading = 1f - p
    return when {
        moon || theme == ThemeKind.NIGHT -> PokeFrame(
            rotation = 14f * sin(4f * PI.toFloat() * p) * fading,
            dy = -4f * arc,
            starBoost = arc,
        )
        theme == ThemeKind.MORNING -> {
            // Crouches, springs up stretched, and lands with a little squash.
            val hop = sin(PI.toFloat() * ((p - 0.15f) / 0.7f).coerceIn(0f, 1f))
            val squash = if (p < 0.15f) sin(PI.toFloat() * p / 0.15f) else if (p > 0.85f) sin(PI.toFloat() * (p - 0.85f) / 0.15f) else 0f
            PokeFrame(
                dy = -30f * hop,
                scaleX = 1f - 0.06f * hop + 0.12f * squash,
                scaleY = 1f + 0.08f * hop - 0.12f * squash,
                delighted = true,
            )
        }
        theme == ThemeKind.NOON -> PokeFrame(
            rotation = 360f * FastOutSlowInEasing.transform(p),
            scaleX = 1f + 0.12f * arc,
            scaleY = 1f + 0.12f * arc,
            delighted = true,
        )
        else -> PokeFrame(
            rotation = 16f * sin(6f * PI.toFloat() * p) * fading,
            dx = 5f * sin(6f * PI.toFloat() * p) * fading,
            scaleX = 1f + 0.05f * arc,
            scaleY = 1f + 0.05f * arc,
            delighted = true,
        )
    }
}

/** How long a poke lasts. */
const val POKE_MILLIS = 1100
