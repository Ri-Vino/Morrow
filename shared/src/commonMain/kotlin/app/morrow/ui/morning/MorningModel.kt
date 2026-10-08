package app.morrow.ui.morning

import app.morrow.ui.formatDuration
import app.morrow.ui.hhmm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class MorningModel(val hello: String, val summary: String)

/**
 * The Morning screen's words for waking at [wokeAt]. [wentToBed] is a guess at bedtime: the summary
 * gives a length of sleep only when the guess makes a believable night.
 */
fun morningModel(wokeAt: Instant, wentToBed: Instant?, zone: TimeZone): MorningModel {
    val woke = wokeAt.toLocalDateTime(zone)
    val time = hhmm(woke.hour, woke.minute)
    val slept = wentToBed?.let { wokeAt - it }?.takeIf { it in BELIEVABLE_NIGHT }
    return MorningModel(
        hello = when {
            woke.hour < 12 -> "Good morning."
            woke.hour < 17 -> "Good afternoon."
            else -> "Good evening."
        },
        summary = if (slept != null) {
            "You were up at $time, after about ${formatDuration(slept)} of sleep."
        } else {
            "You were up at $time. Nicely done."
        },
    )
}

private val BELIEVABLE_NIGHT = 2.hours..14.hours
