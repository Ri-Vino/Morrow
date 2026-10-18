package app.morrow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.weather.Feel
import app.morrow.weather.Warmth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What Home's sky shows for how it feels outside: each from 0 for none to 1 for the most. */
data class ClimateLook(
    /** Frost creeping in from the lower corners, as on a window on a cold morning. */
    val frost: Float,
    /** Haze shimmering over the hills and a warm wash low in the sky. */
    val heat: Float,
    /** Leaves blowing across in a strong wind. Not while it's snowing: the snow blows instead. */
    val leaves: Boolean,
) {
    val none: Boolean get() = frost == 0f && heat == 0f && !leaves

    companion object {
        val None = ClimateLook(frost = 0f, heat = 0f, leaves = false)
    }
}

fun climateLook(feel: Feel, snowing: Boolean): ClimateLook = ClimateLook(
    frost = when (feel.warmth) {
        Warmth.FREEZING -> 1f
        Warmth.COLD -> 0.55f
        else -> 0f
    },
    heat = when (feel.warmth) {
        Warmth.HOT -> 0.6f
        Warmth.SCORCHING -> 1f
        else -> 0f
    },
    leaves = feel.windy && !snowing,
)

/**
 * Frost, heat haze or blowing leaves over Home's 390 × 330 sky, as [look] says. The frost grows in from the corners when
 * it first shows. With "remove animations" on, the frost is simply there, the haze holds still and no leaves blow.
 * Decorative.
 */
@Composable
fun ClimateLayer(look: ClimateLook, modifier: Modifier = Modifier) {
    if (look.none) return
    val seconds = rememberSceneSeconds(still = STILL)
    val reduceMotion = MorrowTheme.reduceMotion
    val growth = remember { Animatable(0f) }
    LaunchedEffect(look.frost > 0f, reduceMotion) {
        if (look.frost == 0f) growth.snapTo(0f)
        else if (reduceMotion) growth.snapTo(1f)
        else growth.animateTo(1f, tween(FROST_GROW_MILLIS, easing = EaseOut))
    }
    Canvas(modifier.graphicsLayer {}) {
        scale(size.width / SCENE_W, Offset.Zero) {
            val t = seconds.value
            if (look.heat > 0f) drawHeat(look.heat, t)
            if (look.leaves && t != STILL) drawLeaves(t)
            if (look.frost > 0f) drawFrost(look.frost, growth.value)
        }
    }
}

/** A warm glow behind Pip on a hot day, [heat] from 0 to 1, pulsing slowly. Drawn in scene units around Pip's centre. */
internal fun DrawScope.drawHeatGlow(heat: Float, seconds: Float) {
    if (heat <= 0f) return
    val pulse = 1f + 0.06f * sin(2f * PI.toFloat() * seconds / 3f)
    val radius = 62f * (1.35f + 0.35f * heat) * pulse
    drawCircle(
        Brush.radialGradient(listOf(Illustration.heatGlow.copy(alpha = 0.5f * heat), Illustration.heatGlow.copy(alpha = 0f)), Offset.Zero, radius),
        radius,
        Offset.Zero,
    )
}

private fun DrawScope.drawHeat(heat: Float, t: Float) {
    // A warm wash rising from the hills.
    drawRect(
        Brush.verticalGradient(
            listOf(Illustration.heatGlow.copy(alpha = 0f), Illustration.heatGlow.copy(alpha = 0.22f * heat)),
            startY = 150f,
            endY = SCENE_BOTTOM,
        ),
        Offset(0f, 150f),
        Size(SCENE_W, SCENE_BOTTOM - 150f),
    )
    // Wavy lines of haze over the hilltops, rippling sideways and rising a little.
    val still = t == STILL
    for (i in 0 until HAZE_LINES) {
        val y = 206f + 12f * i - if (still) 0f else 4f * (t / 2f % 1f)
        val phase = if (still) i.toFloat() else t * (1.6f + 0.3f * i) + i
        val path = Path()
        var x = -10f
        path.moveTo(x, y + 2.2f * sin(phase))
        while (x < SCENE_W + 10f) {
            x += 6f
            path.lineTo(x, y + 2.2f * sin(phase + x / 14f))
        }
        drawPath(path, Illustration.heatHaze, alpha = (0.28f + 0.12f * heat) * heat, style = Stroke(1.8f, cap = StrokeCap.Round))
    }
}

/** Leaves tumbling across from right to left, the way the wind blows, each crossing in 3.2 s. */
private fun DrawScope.drawLeaves(t: Float) {
    for (i in 0 until LEAVES) {
        val p = ((t + i * LEAF_SECONDS / LEAVES) / LEAF_SECONDS) % 1f
        val x = SCENE_W + 20f - (SCENE_W + 40f) * p
        val y = 70f + 26f * i + 12f * sin(2f * PI.toFloat() * (p * 2f + i * 0.3f))
        translate(x, y) {
            rotate(360f * p * (if (i % 2 == 0) 1.5f else -1.2f) + 40f * i, Offset.Zero) {
                drawPath(LeafPaths.Leaf, if (i % 2 == 0) Illustration.leafWarm else Illustration.leafGreen)
                drawLine(Illustration.sunInk.copy(alpha = 0.35f), Offset(0f, -6f), Offset(0f, 6f), strokeWidth = 0.9f)
            }
        }
    }
}

/** Crystals in both lower corners, grown as far as [growth] from 0 to 1, as strong as [frost]. */
private fun DrawScope.drawFrost(frost: Float, growth: Float) {
    if (growth <= 0f) return
    for (right in listOf(false, true)) {
        val corner = Offset(if (right) SCENE_W else 0f, SCENE_BOTTOM)
        val reach = FROST_REACH * (0.7f + 0.3f * frost)
        drawCircle(
            Brush.radialGradient(listOf(Illustration.frost.copy(alpha = 0.45f * frost), Illustration.frost.copy(alpha = 0f)), corner, reach * growth),
            reach * growth,
            corner,
        )
        translate(corner.x, corner.y) {
            scale(if (right) -growth else growth, growth, Offset.Zero) {
                scale(reach / FROST_REACH, Offset.Zero) {
                    drawPath(FrostPaths.Crystals, Illustration.frost, alpha = 0.7f * frost, style = Stroke(1.1f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** Branching frost from a corner at the origin, growing up and to the right, in scene units. */
private fun frostCrystals(): Path {
    val path = Path()
    fun branch(x: Float, y: Float, angle: Float, length: Float, depth: Int) {
        val dx = cos(angle)
        val dy = sin(angle)
        path.moveTo(x, y)
        path.lineTo(x + dx * length, y + dy * length)
        if (depth == 0) return
        for ((at, side) in listOf(0.4f to 1, 0.75f to -1)) {
            val bx = x + dx * length * at
            val by = y + dy * length * at
            branch(bx, by, angle + side * 0.75f, length * 0.38f, depth - 1)
            branch(bx, by, angle - side * 0.75f, length * 0.28f, depth - 1)
        }
    }
    // Four arms fanning from the corner, between nearly flat and nearly straight up.
    val arms = listOf(-0.15f to 84f, -0.6f to 66f, -1.0f to 76f, -1.4f to 58f)
    for ((angle, length) in arms) branch(0f, 0f, angle, length, depth = 2)
    return path
}

/** Built on first draw, so the pure functions above never touch the graphics path code. */
private object FrostPaths {
    val Crystals = frostCrystals()
}

private object LeafPaths {
    val Leaf = svgPath("M0 -7 Q6 0 0 7 Q-6 0 0 -7 Z")
}

private const val SCENE_W = 390f
/** Where the alarms start over the hills, which the frost grows up from. */
private const val SCENE_BOTTOM = 262f
private const val FROST_REACH = 96f
private const val FROST_GROW_MILLIS = 2600
private const val HAZE_LINES = 4
private const val LEAVES = 6
private const val LEAF_SECONDS = 3.2f
private const val STILL = -1f
