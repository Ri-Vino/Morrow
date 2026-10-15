package app.morrow.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalMorrowColors = staticCompositionLocalOf<MorrowColors> { error("No MorrowTheme in the composition") }
private val LocalMorrowType = staticCompositionLocalOf<MorrowType> { error("No MorrowTheme in the composition") }
private val LocalDim = staticCompositionLocalOf { false }

object MorrowTheme {
    val colors: MorrowColors
        @Composable @ReadOnlyComposable get() = LocalMorrowColors.current

    val type: MorrowType
        @Composable @ReadOnlyComposable get() = LocalMorrowType.current

    /** True when a day theme is showing its dim version because the phone is in dark mode. */
    val dim: Boolean
        @Composable @ReadOnlyComposable get() = LocalDim.current
}

/**
 * Provides [kind]'s colours, cross-fading over 800 ms when the theme or the phone's dark mode changes. The theme still
 * follows the clock in dark mode; [dim] only swaps a day theme for its dark version.
 */
@Composable
fun MorrowTheme(kind: ThemeKind, dim: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val dimmed = dim && kind != ThemeKind.NIGHT
    val target = kind.colors(dimmed)
    val colors = MorrowColors(
        bg = target.bg.animated(),
        surface = target.surface.animated(),
        surface2 = target.surface2.animated(),
        line = target.line.animated(),
        text = target.text.animated(),
        muted = target.muted.animated(),
        accent = target.accent.animated(),
        onAccent = target.onAccent.animated(),
        hi = target.hi.animated(),
        warn = target.warn.animated(),
        onWarn = target.onWarn.animated(),
        knobOff = target.knobOff.animated(),
        shadow = target.shadow.animated(),
        hillTint = target.hillTint.animated(),
        houseTint = target.houseTint.animated(),
    )
    CompositionLocalProvider(
        LocalMorrowColors provides colors,
        LocalMorrowType provides morrowType(),
        LocalDim provides dimmed,
        LocalIndication provides PressShrink,
        content = content,
    )
}

@Composable
private fun Color.animated(): Color = animateColorAsState(this, tween(durationMillis = 800)).value
