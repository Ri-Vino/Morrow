package app.morrow.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.morrow.theme.MorrowTheme

/** Goes from 0 to 1 as a sheet comes in, from the moment it's first shown. */
@Composable
fun rememberSheetEntrance(): State<Float> {
    val shown = remember { Animatable(0f) }
    val reduceMotion = MorrowTheme.reduceMotion
    // With less motion the sheet is simply there, rather than sliding up.
    LaunchedEffect(Unit) { if (reduceMotion) shown.snapTo(1f) else shown.animateTo(1f, tween(SHEET_IN_MILLIS, easing = FastOutSlowInEasing)) }
    return shown.asState()
}

/** Slides a sheet up from just below the screen as [entrance] goes from 0 to 1. */
fun Modifier.risingWith(entrance: State<Float>): Modifier = graphicsLayer { translationY = (1f - entrance.value) * size.height }

/** Fades the dimmed screen behind a sheet in as [entrance] goes from 0 to 1. */
fun Modifier.fadingWith(entrance: State<Float>): Modifier = graphicsLayer { alpha = entrance.value }

/**
 * From one screen to the next: going [forward], the new one slides in a little from the right as the old one drifts
 * left and fades; going back, the other way round. With [reduceMotion] the screens only cross-fade.
 */
fun <S> AnimatedContentTransitionScope<S>.screenChange(forward: Boolean, reduceMotion: Boolean = false): ContentTransform {
    if (reduceMotion) return fadeIn(tween(SCREEN_FADE_MILLIS, delayMillis = SCREEN_FADE_DELAY_MILLIS)) togetherWith fadeOut(tween(SCREEN_FADE_MILLIS))
    val way = if (forward) 1 else -1
    val enter = fadeIn(tween(SCREEN_FADE_MILLIS, delayMillis = SCREEN_FADE_DELAY_MILLIS)) +
        slideInHorizontally(tween(SCREEN_SLIDE_MILLIS, easing = LinearOutSlowInEasing)) { width -> way * width / 6 }
    val exit = fadeOut(tween(SCREEN_FADE_MILLIS)) +
        slideOutHorizontally(tween(SCREEN_SLIDE_MILLIS, easing = FastOutSlowInEasing)) { width -> -way * width / 12 }
    return enter togetherWith exit
}

private const val SHEET_IN_MILLIS = 280
private const val SCREEN_SLIDE_MILLIS = 320
private const val SCREEN_FADE_MILLIS = 180
private const val SCREEN_FADE_DELAY_MILLIS = 60
