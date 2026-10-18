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
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** What Home's sky shows for how it feels outside: each from 0 for none to 1 for the most. */
data class ClimateLook(
    /** Frost creeping in from the corners, as on a window on a cold morning. */
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
 * it first shows, and glints here and there; its lines are shaded a little to stand out on a [lightSky]. With "remove
 * animations" on, the frost is simply there, the haze holds still and no leaves blow. Decorative.
 */
@Composable
fun ClimateLayer(look: ClimateLook, modifier: Modifier = Modifier, lightSky: Boolean = true) {
    if (look.none) return
    val seconds = rememberSceneSeconds(still = STILL)
    val reduceMotion = MorrowTheme.reduceMotion
    val growth = remember { Animatable(0f) }
    LaunchedEffect(look.frost > 0f, reduceMotion) {
        if (look.frost == 0f) growth.snapTo(0f)
        else if (reduceMotion) growth.snapTo(1f)
        else growth.animateTo(1f, tween(FROST_GROW_MILLIS, easing = EaseOut))
    }
    // The frost only changes while it grows, so it has a layer of its own that the moving parts don't redraw.
    if (look.frost > 0f) {
        Canvas(modifier.graphicsLayer {}) {
            scale(size.width / SCENE_W, Offset.Zero) { drawFrost(look.frost, growth.value, lightSky) }
        }
    }
    Canvas(modifier.graphicsLayer {}) {
        scale(size.width / SCENE_W, Offset.Zero) {
            val t = seconds.value
            if (look.heat > 0f) drawHeat(look.heat, t)
            if (look.leaves && t != STILL) drawLeaves(t)
            if (look.frost > 0f) drawFrostGlints(look.frost, growth.value, t)
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

/**
 * Each corner the frost grows from: where it is, which way it grows (its shapes grow up and to the right, so -1 mirrors
 * them), and how big it gets. The lower two meet the alarms over the hills; the smaller upper one keeps clear of the
 * words.
 */
private class FrostCorner(val x: Float, val y: Float, val flipX: Float, val flipY: Float, val size: Float)

private val FrostCorners = listOf(
    FrostCorner(0f, SCENE_BOTTOM, 1f, 1f, 0.8f),
    FrostCorner(SCENE_W, SCENE_BOTTOM, -1f, 1f, 0.95f),
    FrostCorner(SCENE_W, 0f, -1f, -1f, 0.5f),
)

/** Runs [block] in the frame of [corner], grown to [growth] of its size, as big as [frost] makes it. */
private inline fun DrawScope.inCorner(corner: FrostCorner, frost: Float, growth: Float, block: DrawScope.() -> Unit) {
    val grown = corner.size * (0.7f + 0.3f * frost) * (0.45f + 0.55f * growth)
    translate(corner.x, corner.y) {
        scale(corner.flipX * grown, corner.flipY * grown, Offset.Zero) { block() }
    }
}

/**
 * Frost in the corners, grown as far as [growth] from 0 to 1, as strong as [frost]: a cold blue tint, a crust of rime
 * packed into the corner, and feathery fern-like crystals branching out from it among a few loose snowflake crystals.
 */
private fun DrawScope.drawFrost(frost: Float, growth: Float, lightSky: Boolean) {
    if (growth <= 0f) return
    val shapes = FrostShapes.get
    val strength = frost * growth
    val tint = if (lightSky) 0.55f else 0.22f
    for (corner in FrostCorners) {
        inCorner(corner, frost, growth) {
            scale(FROST_REACH * 1.25f, Offset.Zero) { drawCircle(FrostBrushes.Tint, 1f, Offset.Zero, alpha = tint * strength) }
            if (lightSky) {
                // A faint shadow to one side picks the white lines out of a pale sky.
                translate(0.7f, 0.7f) {
                    drawPath(shapes.stems, Illustration.frostShade, alpha = 0.35f * strength, style = FrostStrokes.Stem)
                    drawPath(shapes.barbs, Illustration.frostShade, alpha = 0.22f * strength, style = FrostStrokes.Barb)
                }
            }
            drawPath(shapes.crust, Illustration.frost, alpha = (if (lightSky) 0.75f else 0.5f) * strength)
            drawPath(shapes.barbs, Illustration.frost, alpha = (if (lightSky) 0.75f else 0.6f) * strength, style = FrostStrokes.Barb)
            drawPath(shapes.stems, Illustration.frost, alpha = (if (lightSky) 0.95f else 0.8f) * strength, style = FrostStrokes.Stem)
            drawPath(shapes.crystals, Illustration.frost, alpha = 0.85f * strength * growth, style = FrostStrokes.Barb)
        }
    }
}

/** Little four-pointed glints at the frost's tips, each catching the light for a moment in turn. Some shine when still. */
private fun DrawScope.drawFrostGlints(frost: Float, growth: Float, t: Float) {
    if (growth < 0.8f) return
    val shapes = FrostShapes.get
    val shown = (growth - 0.8f) / 0.2f * frost
    FrostCorners.forEachIndexed { c, corner ->
        inCorner(corner, frost, growth) {
            shapes.glints.forEachIndexed { k, at ->
                val glint = if (t == STILL) {
                    if ((k + c) % 3 == 0) 0.8f else 0f
                } else {
                    val wave = sin(2f * PI.toFloat() * (t / GLINT_SECONDS + k * 0.37f + c * 0.21f))
                    if (wave > 0.55f) (wave - 0.55f) / 0.45f else 0f
                }
                if (glint > 0f) {
                    translate(at.x, at.y) {
                        scale(3.2f + 2.2f * glint, Offset.Zero) { drawPath(FrostShapes.Glint, Illustration.frost, alpha = shown * glint) }
                    }
                }
            }
        }
    }
}

/** The frost's shapes, from a corner at the origin growing up and to the right, in scene units. */
private class FrostShapes {
    /** The main ferns' stems, drawn heavier. */
    val stems = Path()

    /** Their barbs, and the barbs' own. */
    val barbs = Path()

    /** The rime packed into the corner, its edge jagged with crystals. */
    val crust = Path()

    /** Loose six-armed crystals among the ferns. */
    val crystals = Path()

    /** Where the frost glints: the ferns' tips. */
    val glints = mutableListOf<Offset>()

    init {
        for (fern in Ferns) fern(fern.x, fern.y, fern.heading, fern.length, fern.curl, depth = 2)
        crust()
        for ((at, size) in Crystals) crystal(at, size)
    }

    /**
     * A fern of frost: a gently curving stem with barbs down both sides that point toward its tip and shorten toward
     * it. At [depth] 2 each barb is a small fern of its own.
     */
    private fun fern(x0: Float, y0: Float, heading0: Float, length: Float, curl: Float, depth: Int) {
        val path = if (depth == 2) stems else barbs
        val joints = if (depth == 2) 8 else 5
        val step = length / joints
        var x = x0
        var y = y0
        var heading = heading0
        for (k in 1..joints) {
            heading += curl
            val nx = x + cos(heading) * step
            val ny = y + sin(heading) * step
            path.moveTo(x, y)
            path.lineTo(nx, ny)
            x = nx
            y = ny
            if (k == joints) break
            val left = 1f - k.toFloat() / joints
            val barb = length * (if (depth == 2) 0.34f else 0.42f) * sqrt(left) * if (k == 1) 0.7f else 1f
            for (side in Sides) {
                val angle = heading + side * (0.85f + 0.1f * (k % 2))
                if (depth == 2) {
                    fern(x, y, angle, barb, -side * 0.06f, depth = 1)
                } else {
                    barbs.moveTo(x, y)
                    barbs.lineTo(x + cos(angle) * barb, y + sin(angle) * barb)
                }
            }
        }
        if (depth == 2) glints += Offset(x, y)
    }

    /** A crust in the corner whose edge zigzags in and out like packed crystals. */
    private fun crust() {
        crust.moveTo(0f, 0f)
        val points = 15
        for (k in 0..points) {
            val angle = -PI.toFloat() / 2f * k / points
            val radius = if (k % 2 == 0) 15f + 6f * fract(k * 0.618f) else 24f + 7f * fract(k * 0.382f)
            crust.lineTo(cos(angle) * radius, sin(angle) * radius)
        }
        crust.close()
    }

    /** A snowflake's six arms, each with a pair of little ticks near its end. */
    private fun crystal(at: Offset, size: Float) {
        for (arm in 0 until 6) {
            val angle = arm * PI.toFloat() / 3f
            val direction = Offset(cos(angle), sin(angle))
            val end = at + direction * size
            crystals.moveTo(at.x, at.y)
            crystals.lineTo(end.x, end.y)
            val tick = at + direction * (size * 0.6f)
            for (side in Sides) {
                val tickAngle = angle + side * 0.8f
                crystals.moveTo(tick.x, tick.y)
                crystals.lineTo(tick.x + cos(tickAngle) * size * 0.35f, tick.y + sin(tickAngle) * size * 0.35f)
            }
        }
    }

    companion object {
        /** Built on first draw, so the pure functions above never touch the graphics path code. */
        val get by lazy { FrostShapes() }
        val Glint by lazy { svgPath("M0 -1 L0.2 -0.2 L1 0 L0.2 0.2 L0 1 L-0.2 0.2 L-1 0 L-0.2 -0.2 Z") }
    }
}

private val Sides = floatArrayOf(-1f, 1f)

private class Fern(val x: Float, val y: Float, val heading: Float, val length: Float, val curl: Float)

// Five ferns fanning out from the corner, from along its bottom edge to up its side.
private val Ferns = listOf(
    Fern(8f, -3f, -0.06f, 100f, -0.025f),
    Fern(10f, -10f, -0.6f, 64f, 0.045f),
    Fern(8f, -14f, -0.98f, 108f, -0.035f),
    Fern(4f, -12f, -1.3f, 74f, 0.05f),
    Fern(2f, -8f, -1.52f, 92f, -0.04f),
)

// Close to the corner, so none lands on Pip when he sits low in the sky.
private val Crystals = listOf(Offset(36f, -46f) to 4.5f, Offset(72f, -24f) to 3.5f, Offset(18f, -80f) to 4f)

private fun fract(x: Float): Float = x - floor(x)

/** Built on first draw. The tint is drawn at a radius of 1 and scaled up to each corner. */
private object FrostBrushes {
    val Tint = Brush.radialGradient(
        listOf(Illustration.frostTint, Illustration.frostTint.copy(alpha = 0.55f), Illustration.frostTint.copy(alpha = 0f)),
        Offset.Zero,
        1f,
    )
}

private object FrostStrokes {
    val Stem = Stroke(1.5f, cap = StrokeCap.Round)
    val Barb = Stroke(0.85f, cap = StrokeCap.Round)
}

private object LeafPaths {
    val Leaf = svgPath("M0 -7 Q6 0 0 7 Q-6 0 0 -7 Z")
}

private const val SCENE_W = 390f
/** Where the alarms start over the hills, which the frost grows up from. */
private const val SCENE_BOTTOM = 262f
private const val FROST_REACH = 110f
private const val FROST_GROW_MILLIS = 2600
private const val HAZE_LINES = 4
private const val LEAVES = 6
private const val LEAF_SECONDS = 3.2f
private const val STILL = -1f
private const val GLINT_SECONDS = 3.4f
