package app.morrow.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.morrow.theme.Holiday
import app.morrow.theme.Illustration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

// Holiday touches for Home's 390 × 330 scene, in its units. Each moves on the scene clock, so with "remove animations"
// on it holds still at the clock's 0 and the scene keeps its decorations.

/** The colours fireworks burst in, to suit the sky behind them. */
internal enum class FireworkSky(val colors: List<Color>, val glitter: Color, val night: Boolean) {
    NIGHT(Illustration.fireworksNight, Illustration.fireworkGlitterNight, night = true),
    DAY(Illustration.fireworksDay, Illustration.fireworkGlitterDay, night = false),
    DUSK(Illustration.fireworksDusk, Illustration.fireworkGlitterDay, night = false),
}

/**
 * New Year's fireworks for [show], as bright as [alpha]. A shell climbs from behind the hills every 0.9 s and bursts at
 * the top of its climb into a round of sparks, a ring around a smaller ring, or gold glitter that crackles; the sparks
 * fly out, slow, fade and fall, trailing as they go. Each comes from one of six places, in an order that shifts every
 * round, so two or three are always in the sky. At 0, as with "remove animations" on, one is opening, one is spread
 * wide and the next is on its way up.
 */
internal fun DrawScope.drawFireworks(sky: FireworkSky, t: Float, show: FireworkShow = FireworkShow.Home, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val spots = show.spots
    for (i in 0 until SHELLS) {
        val since = t + FIRST_SHELL - i * SHELL_GAP
        val round = floor(since / ROUND).toInt()
        val age = since - round * ROUND
        if (age > RISE + BLOOM) continue
        val spot = spots[(i + 2 * round).mod(spots.size)]
        if (age < RISE) {
            drawRisingShell(spot, show.launchY, age / RISE, sky, alpha)
            continue
        }
        val colour = (2 * i + round).mod(sky.colors.size)
        val bloom = (age - RISE) / BLOOM
        when ((i + round).mod(3)) {
            0 -> drawBurst(spot, bloom, spot.reach, sky.colors[colour], Sparks.Round, sky, alpha, glitter = false)
            1 -> {
                drawBurst(spot, bloom, spot.reach, sky.colors[colour], Sparks.Ring, sky, alpha, glitter = false)
                drawBurst(spot, bloom, spot.reach * 0.55f, sky.colors[(colour + 2) % sky.colors.size], Sparks.Inner, sky, alpha, glitter = false)
            }
            else -> drawBurst(spot, bloom, spot.reach * 1.1f, sky.glitter, Sparks.Round, sky, alpha, glitter = true)
        }
    }
}

/** A shell on its way up from behind the hills, [rise] from 0 to 1, slowing as it nears the top, its tail behind it. */
private fun DrawScope.drawRisingShell(spot: Spot, launchY: Float, rise: Float, sky: FireworkSky, alpha: Float) {
    val head = spot.at(launchY, easeOutQuad(rise))
    val tail = spot.at(launchY, easeOutQuad((rise - 0.25f).coerceAtLeast(0f)))
    val colour = if (sky.night) Illustration.fireworkCore else sky.glitter
    drawLine(colour, tail, head, strokeWidth = 1.4f, cap = StrokeCap.Round, alpha = 0.55f * alpha)
    drawCircle(colour, 1.6f, head, alpha = 0.9f * alpha)
}

/**
 * One burst of [sparks], [bloom] from 0 just burst to 1 gone, reaching [reach] from the shell. The sparks shoot out fast
 * and slow down while gravity takes them, each drawn as a streak from where it was a moment ago. By night the shell
 * flashes as it bursts and each spark has a white-hot head; by day a little smoke hangs where it burst. [glitter]
 * sparks fall further, trail longer and crackle as they go out.
 */
private fun DrawScope.drawBurst(
    spot: Spot,
    bloom: Float,
    reach: Float,
    colour: Color,
    sparks: Sparks,
    sky: FireworkSky,
    alpha: Float,
    glitter: Boolean,
) {
    // Holds its brightness while it opens, then fades as it falls.
    val fade = (1f - bloom * bloom) * (1f - 0.3f * bloom) * alpha
    val fall = reach * if (glitter) 0.9f else 0.55f
    // Streaks lengthen as the sparks fall.
    val before = (bloom - (if (glitter) 0.16f else 0.1f) - 0.16f * bloom).coerceAtLeast(0f)
    val now = spread(bloom)
    val then = spread(before)
    val drop = fall * bloom * bloom
    val dropBefore = fall * before * before
    if (sky.night && bloom < FLASH) {
        translate(spot.x, spot.y) {
            scale(reach * 1.1f, Offset.Zero) { drawCircle(FireworkBrushes.Flash, 1f, Offset.Zero, alpha = 0.6f * alpha * (1f - bloom / FLASH)) }
        }
    } else if (!sky.night && bloom > 0.15f) {
        val haze = (bloom - 0.15f) / 0.85f
        translate(spot.x, spot.y + 6f * haze) {
            scale(reach * (0.55f + 0.35f * haze), Offset.Zero) {
                drawCircle(FireworkBrushes.Smoke, 1f, Offset.Zero, alpha = 0.45f * alpha * (1f - haze))
            }
        }
    }
    val width = 2f * (1f - 0.45f * bloom)
    for (j in 0 until sparks.count) {
        val dx = sparks.dx[j] * sparks.speed[j] * reach
        val dy = sparks.dy[j] * sparks.speed[j] * reach
        val head = Offset(spot.x + dx * now, spot.y + dy * now + drop)
        val tail = Offset(spot.x + dx * then, spot.y + dy * then + dropBefore)
        val crackle = if (glitter && bloom > 0.45f) 0.45f + 0.55f * abs(sin(31f * bloom + j * 1.7f)) else 1f
        val shown = fade * crackle
        drawLine(colour, tail, head, strokeWidth = width, cap = StrokeCap.Round, alpha = 0.75f * shown)
        drawCircle(colour, width * 0.9f, head, alpha = shown)
        if (sky.night && bloom < 0.6f) drawCircle(Illustration.fireworkCore, width * 0.5f, head, alpha = shown * (1f - bloom / 0.6f))
    }
}

/** How far out the sparks have flown, from 0 to 1 of their reach: fast at first, then slowing as the air holds them. */
private fun spread(bloom: Float): Float = (1f - exp(-DRAG * bloom)) / (1f - exp(-DRAG))

private fun easeOutQuad(x: Float): Float = 1f - (1f - x) * (1f - x)

/** Where a shell bursts, how far its sparks reach, and how far across it was fired from. */
internal class Spot(val x: Float, val y: Float, val reach: Float, val launchX: Float) {
    /** Where the shell is, [rise] of the way up from [launchY]. */
    fun at(launchY: Float, rise: Float): Offset = Offset(launchX + (x - launchX) * rise, launchY + (y - launchY) * rise)
}

/** Where a show's shells burst, taking turns left and right, and how low, out of sight, they're fired from. */
internal class FireworkShow(val spots: List<Spot>, val launchY: Float) {
    companion object {
        /** Home's: low in the sky, under the date, greeting and weather line and either side of Pip. */
        val Home = FireworkShow(
            listOf(
                Spot(166f, 194f, 46f, 156f),
                Spot(350f, 150f, 42f, 364f),
                Spot(104f, 204f, 38f, 118f),
                Spot(226f, 184f, 46f, 240f),
                Spot(362f, 196f, 34f, 348f),
                Spot(140f, 182f, 40f, 130f),
            ),
            launchY = 262f,
        )

        /** The ring screen's, in its 390 × 844 units: either side of the rising Pip, below the alarm's words. */
        val Ring = FireworkShow(
            listOf(
                Spot(82f, 424f, 66f, 70f),
                Spot(312f, 404f, 62f, 330f),
                Spot(110f, 486f, 52f, 96f),
                Spot(290f, 476f, 56f, 304f),
                Spot(52f, 520f, 46f, 40f),
                Spot(344f, 524f, 46f, 356f),
            ),
            launchY = 720f,
        )
    }
}

/** The directions a burst's sparks fly in, as unit vectors, and how fast each goes, so no burst is a perfect circle. */
private class Sparks(val count: Int, turn: Float, jitter: Float, seed: Int) {
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val speed = FloatArray(count)

    init {
        for (j in 0 until count) {
            val wobble = fract(sin((j + seed) * 12.9898f) * 43758.547f)
            val angle = 2f * PI.toFloat() * (j + turn) / count + (wobble - 0.5f) * 0.18f
            dx[j] = cos(angle)
            dy[j] = sin(angle)
            speed[j] = 1f - jitter * fract(wobble * 7.13f)
        }
    }

    companion object {
        val Round = Sparks(30, turn = 0f, jitter = 0.3f, seed = 3)
        val Ring = Sparks(24, turn = 0.25f, jitter = 0.06f, seed = 11)
        val Inner = Sparks(14, turn = 0.5f, jitter = 0.12f, seed = 23)
    }
}

private fun fract(x: Float): Float = x - floor(x)

/** Built on first draw, so pure code never makes a shader. Drawn at a radius of 1 and scaled up to each burst. */
private object FireworkBrushes {
    val Flash = Brush.radialGradient(
        listOf(Illustration.fireworkCore.copy(alpha = 0.7f), Illustration.fireworkCore.copy(alpha = 0.18f), Illustration.fireworkCore.copy(alpha = 0f)),
        Offset.Zero,
        1f,
    )
    val Smoke = Brush.radialGradient(
        listOf(Illustration.fireworkSmoke.copy(alpha = 0.5f), Illustration.fireworkSmoke.copy(alpha = 0f)),
        Offset.Zero,
        1f,
    )
}

private const val SHELLS = 6
private const val SHELL_GAP = 0.9f
private const val ROUND = SHELLS * SHELL_GAP
/** How far the first shell has got at 0: just burst. */
private const val FIRST_SHELL = 1.15f
private const val RISE = 0.75f
private const val BLOOM = 2.1f
private const val FLASH = 0.22f
private const val DRAG = 3f

/**
 * Confetti fluttering down over the scene at New Year, each piece turning over as it falls, so it flashes thin and
 * wide. It stops at the hills.
 */
internal fun DrawScope.drawConfetti(t: Float) {
    for (piece in Confetti) {
        val p = ((t + piece.delay) / piece.fall) % 1f
        val x = piece.x + piece.sway * sin(2f * PI.toFloat() * (p * 2f + piece.delay * 0.1f))
        val y = -8f + 266f * p
        val alpha = if (p < 0.06f) p / 0.06f else if (p > 0.88f) (1f - p) / 0.12f else 1f
        val flip = 0.2f + 0.8f * abs(cos(2f * PI.toFloat() * (t / piece.flip + piece.delay)))
        translate(x, y) {
            rotate(piece.tilt + 40f * sin(2f * PI.toFloat() * (p * 3f + piece.delay)), Offset.Zero) {
                scale(flip, 1f, Offset.Zero) {
                    if (piece.round) drawCircle(piece.color, 1.9f, Offset.Zero, alpha = alpha)
                    else drawRect(piece.color, Offset(-1.7f, -2.8f), Size(3.4f, 5.6f), alpha = alpha)
                }
            }
        }
    }
}

private class ConfettiPiece(
    val x: Float,
    val fall: Float,
    val delay: Float,
    val sway: Float,
    val flip: Float,
    val tilt: Float,
    val round: Boolean,
    val color: Color,
)

private val Confetti = List(18) { i ->
    ConfettiPiece(
        x = (11f + 67f * i) % 380f + 5f,
        fall = 7f + (i * 7 % 5),
        delay = i * 1.37f,
        sway = 6f + (i % 4) * 3f,
        flip = 0.9f + (i % 3) * 0.35f,
        tilt = i * 47f % 180f,
        round = i % 4 == 3,
        color = Illustration.confetti[i % Illustration.confetti.size],
    )
}

/** Three bats flitting across the sky from left to right, each on its own wavy path, flapping as they go. */
internal fun DrawScope.drawBats(t: Float) {
    for ((i, bat) in Bats.withIndex()) {
        val p = ((t + bat.delay) / bat.crossing) % 1f
        val x = -30f + 450f * p
        val y = bat.y + 14f * sin(2f * PI.toFloat() * p * 3f + i)
        val flap = 0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * t / 0.28f + i))
        translate(x, y) {
            scale(bat.size, Offset.Zero) {
                scale(1f, flap, Offset.Zero) { drawPath(HolidayPaths.BatWings, Illustration.bat) }
                drawCircle(Illustration.bat, 3f, Offset.Zero)
                drawPath(HolidayPaths.BatEars, Illustration.bat)
            }
        }
    }
}

private class Bat(val y: Float, val crossing: Float, val delay: Float, val size: Float)

private val Bats = listOf(Bat(70f, 11f, 2f, 1.1f), Bat(128f, 14f, 9f, 0.85f), Bat(46f, 17f, 5f, 0.7f))

/** A carved pumpkin glowing on the hill by the cottage, its candle flickering. */
internal fun DrawScope.drawPumpkin(t: Float) {
    translate(28f, 252f) {
        val flicker = 0.75f + 0.25f * sin(2f * PI.toFloat() * t / 0.9f) * sin(2f * PI.toFloat() * t / 1.7f)
        drawCircle(
            Brush.radialGradient(listOf(Illustration.pumpkin.copy(alpha = 0.45f * flicker), Illustration.pumpkin.copy(alpha = 0f)), Offset.Zero, 24f),
            24f,
            Offset.Zero,
        )
        drawOval(Illustration.pumpkin, Offset(-11f, -8f), Size(22f, 16f))
        drawLine(Illustration.pumpkinStem, Offset(0f, -7f), Offset(2f, -12f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawPath(HolidayPaths.PumpkinFace, Illustration.pumpkinFace, alpha = flicker)
    }
}

/** A little fir with a star, to the right of the cottage. */
internal fun DrawScope.drawTree() {
    translate(122f, 258f) {
        drawPath(HolidayPaths.Tree, Illustration.tree)
        drawPath(HolidayPaths.Star, Illustration.treeStar)
    }
}

/** Fairy lights along the cottage's roof, twinkling in turn. */
internal fun DrawScope.drawRoofLights(t: Float) {
    RoofBulbs.forEachIndexed { i, at ->
        val twinkle = 0.55f + 0.45f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t / 1.6f + i * 0.37f)))
        drawCircle(Illustration.fairyLights[i % Illustration.fairyLights.size], 1.9f, at, alpha = twinkle)
    }
}

// Seven bulbs down each side of the roof, which runs from (44, 240) up to (72, 216) and down to (100, 240).
private val RoofBulbs = List(14) { i ->
    val f = (i % 7 + 0.5f) / 7f
    if (i < 7) Offset(44f + 28f * f, 241f - 24f * f) else Offset(100f - 28f * f, 241f - 24f * f)
}

/** Light snow over the whole scene for Christmas, gentler than a real snowfall, which takes its place when there's one. */
internal fun DrawScope.drawHolidaySnow(t: Float) {
    repeat(FLAKES) { i ->
        val p = ((t + i * 0.53f) / 6f) % 1f
        val x = (17f + 23f * i) % 390f + 8f * sin(2f * PI.toFloat() * (p * 2f + i * 0.2f))
        val y = -10f + 300f * p
        val alpha = if (p < 0.1f) p / 0.1f else if (p > 0.85f) (1f - p) / 0.15f else 1f
        drawCircle(Illustration.snowflake, 1.6f + (i % 3) * 0.4f, Offset(x, y), alpha = 0.85f * alpha)
    }
}

private const val FLAKES = 22

/** Built on first draw, so pure code never touches the graphics path parser. */
private object HolidayPaths {
    val BatWings = svgPath("M0 0 Q-5 -6 -13 -3 Q-10 -1 -11 2 Q-7 0 -5 2 Q-3 0 0 1 Q3 0 5 2 Q7 0 11 2 Q10 -1 13 -3 Q5 -6 0 0 Z")
    val BatEars = svgPath("M-2.2 -2 l-0.6 -3.2 l2 1.6 zM2.2 -2 l0.6 -3.2 l-2 1.6 z")
    val PumpkinFace = svgPath("M-6 -3 l2 -3 l2 3 zM2 -3 l2 -3 l2 3 zM-6 2 q6 4 12 0 l-2 1 l-2 -1 l-2 1 l-2 -1 l-2 1 z")
    val Tree = svgPath("M0 -40 L9 -26 L5 -26 L13 -13 L8 -13 L16 0 L-16 0 L-8 -13 L-13 -13 L-5 -26 L-9 -26 Z")
    val Star = svgPath("M0 -46 l1.8 3.8 l4 0.4 l-3 2.7 l0.9 4 l-3.7 -2.1 l-3.7 2.1 l0.9 -4 l-3 -2.7 l4 -0.4 z")
}
