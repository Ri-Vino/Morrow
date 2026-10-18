package app.morrow.ui.ring

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import app.morrow.domain.Alarm
import app.morrow.domain.UNLIMITED_SNOOZES
import app.morrow.domain.canSnooze
import app.morrow.theme.Illustration
import app.morrow.theme.mix
import app.morrow.ui.components.Icons
import app.morrow.ui.displayName
import app.morrow.ui.edit.zoneName
import app.morrow.ui.gamesText
import app.morrow.ui.hhmm
import app.morrow.ui.world.cityClock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

data class RingModel(
    val time: String,
    val label: String,
    /** The alarm's own line, shown under [label], or null when it has none. */
    val description: String?,
    val missionIcon: String,
    val startLabel: String,
)

/**
 * The ringing screen for [alarm] at [now]. The big time is the phone's clock, in [phoneZone], which after a snooze is
 * later than the alarm was set for; an alarm on another city's clock names its own time there instead of its games.
 */
fun ringModel(alarm: Alarm, now: Instant, phoneZone: TimeZone): RingModel {
    val first = alarm.games.first().type
    val city = alarm.zoneName()
    val about = if (city == null) gamesText(alarm.games) else "${hhmm(alarm.hour, alarm.minute)} in $city"
    return RingModel(
        time = cityClock(now, phoneZone),
        label = listOf(alarm.label.trim(), about).filter(String::isNotEmpty).joinToString(" · "),
        description = alarm.description?.trim()?.takeIf(String::isNotEmpty),
        missionIcon = if (city == null) Icons.forMission(first) else Icons.Globe,
        startLabel = "I’m up — start ${first.displayName.lowercase()}",
    )
}

/** The ring screen's Snooze button for [alarm], snoozed [snoozed] times already, or null when it can't snooze again. */
fun snoozeLabel(alarm: Alarm, snoozed: Int): String? {
    if (!canSnooze(alarm, snoozed)) return null
    val label = "Snooze ${alarm.snoozeMinutes} min"
    if (alarm.snoozeLimit == UNLIMITED_SNOOZES || snoozed == 0) return label
    return "$label · ${alarm.snoozeLimit - snoozed} left"
}

/** Hold the sunrise here to show it finished, for when animations are off. */
const val SUNRISE_DONE = Float.POSITIVE_INFINITY

/** Seconds until the sunrise has finished playing; only the looping parts move after this. */
const val SUNRISE_LENGTH = 7f

/**
 * The parts of the sunrise that play once, [seconds] after the ringing screen appears, as in the design canvas.
 * Alphas and progress run from 0 to 1.
 */
data class SunriseFrame(
    /** The sky's bands, outermost first. */
    val bands: List<Color>,
    val hill: Color,
    val starAlpha: Float,
    /** How far the moon has set, as it slides down and fades. */
    val moonSet: Float,
    /** How far Pip has risen. */
    val rise: Float,
    val raysAlpha: Float,
    val eyesShut: Float,
    val eyesOpen: Float,
    val yawn: Float,
    val smile: Float,
    /** Greeting and time, the alarm label, then Pip's line. */
    val textIn: List<Float>,
)

fun sunriseFrame(seconds: Float): SunriseFrame {
    val sky = EaseOut.at(seconds, start = 0f, length = SUNRISE_LENGTH)
    // Pip's mouth opens into a yawn, then closes as its eyes open and it smiles.
    val yawn = if (seconds < 3.6f) Ease.at(seconds, 2.3f, 0.4f) else 1f - Ease.at(seconds, 3.6f, 0.3f)
    return SunriseFrame(
        bands = Illustration.sunriseBands.map { (night, dawn) -> mix(night, dawn, sky) },
        hill = mix(Illustration.sunriseHill.first, Illustration.sunriseHill.second, sky),
        starAlpha = 1f - Ease.at(seconds, 1f, 5f),
        moonSet = EaseIn.at(seconds, 0f, 6.5f),
        rise = Rise.at(seconds, 0f, 6.5f),
        raysAlpha = Ease.at(seconds, 2.5f, 2f),
        eyesShut = 1f - Ease.at(seconds, 3.6f, 0.25f),
        eyesOpen = Ease.at(seconds, 3.6f, 0.25f),
        yawn = yawn,
        smile = Ease.at(seconds, 3.7f, 0.4f),
        textIn = listOf(0.4f, 0.7f, 4f).map { delay -> Ease.at(seconds, delay, 0.8f) },
    )
}

private val Rise = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/** This easing's value for an animation that starts at [start] seconds and lasts [length]. */
private fun Easing.at(seconds: Float, start: Float, length: Float): Float =
    transform(((seconds - start) / length).coerceIn(0f, 1f))
