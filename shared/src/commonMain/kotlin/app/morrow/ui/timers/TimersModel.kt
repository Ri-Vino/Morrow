package app.morrow.ui.timers

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.Color
import app.morrow.platform.AlarmPermission
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.mix
import app.morrow.timers.Timer
import app.morrow.timers.clockText
import app.morrow.timers.hasEnded
import app.morrow.timers.name
import app.morrow.timers.progress
import app.morrow.timers.remaining
import app.morrow.timers.totalText
import app.morrow.ui.world.cityClock
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Where a timer is in its life, which decides what its card shows and offers. */
enum class TimerState {
    RUNNING,
    PAUSED,

    /** Ended, and the done screen and bell are on it. */
    RINGING,

    /** Ended during an alarm, so it rings after the alarm is done. */
    WAITING,

    /**
     * Ended, and nothing has taken it up yet. This only lasts a moment, except on iOS for a timer the system refused,
     * which never rang: it stays until it's stopped.
     */
    ENDED,
}

/**
 * The sky strip on a timer card, in the design's 358 × 72 units. As the timer runs down the sun sinks from midday to
 * dusk, and Pip dozes off as it sets.
 */
data class TimerSky(
    /** How far the timer has run, 0 to 1. */
    val progress: Float,
    /** The time of day the strip shows: 12:00 when the timer starts, 19:20 as it ends. */
    val minuteOfDay: Float,
    val sky: Color,
    val hillBack: Color,
    val hillFront: Color,
    /** Pip's centre. */
    val sunX: Float,
    val sunY: Float,
    val starAlpha: Float,
    /** Pip's eyes close for the last part of the sky. */
    val sleepy: Boolean,
)

/** The strip for a timer that has run [progress] of its length. */
fun timerSky(progress: Float): TimerSky {
    val f = progress.coerceIn(0f, 1f)
    val minute = SKY_START_MINUTE + f * SKY_SPAN_MINUTES
    val sky = SkyCurve.at(minute)
    return TimerSky(
        progress = f,
        minuteOfDay = minute,
        sky = sky,
        hillBack = mix(sky, Illustration.ground, 0.35f),
        hillFront = mix(sky, Illustration.ground, 0.55f),
        sunX = SUN_LEFT + f * SUN_SPAN,
        sunY = SUN_TOP + f * f * SUN_DROP,
        starAlpha = ((f - STARS_FROM) * 3f).coerceIn(0f, MAX_STAR_ALPHA),
        sleepy = f >= SLEEPY_FROM,
    )
}

/** One timer's card: the sky strip, the time left or that time is up, the note and total, and its buttons. */
data class TimerCard(
    val id: Long,
    val state: TimerState,
    /** "04:12" while it counts down or waits paused, or that time is up. */
    val time: String,
    /** The note and total, with what the timer is doing when it isn't simply running. */
    val detail: String,
    val sky: TimerSky,
    val toggleLabel: String,
    val addLabel: String,
    val removeLabel: String,
) {
    /** A timer that has ended can't be paused; it can only be stopped or given another minute. */
    val canToggle: Boolean get() = state == TimerState.RUNNING || state == TimerState.PAUSED

    /** Whether [time] is a countdown rather than words. */
    val showsClock: Boolean get() = canToggle

    /** A countdown with hours ("1:02:03") is wider than the others, so it is set smaller. */
    val longClock: Boolean get() = showsClock && time.length > SHORT_CLOCK_LENGTH
}

/** What the setter shows: the typed [entry] and [note], and whether it can be put away again. */
data class SetterModel(val entry: TimerEntry, val note: String, val canCancel: Boolean)

/** Everything on the Timers tab. The setter shows on its own with no timers, and above them when a new one is added. */
data class TimersModel(
    val sub: String,
    val setter: SetterModel?,
    val cards: List<TimerCard>,
    /** The "New timer" button, which opens the setter over the running timers. */
    val showNewTimer: Boolean,
)

/** What has been typed into the setter, and whether it was opened over running timers. Kept through rotation. */
data class TimerDraft(val entry: TimerEntry = TimerEntry(), val note: String = "", val adding: Boolean = false) {
    companion object {
        val Saver = listSaver<TimerDraft, Any>(
            save = { listOf(it.entry.digits, it.note, it.adding) },
            restore = { TimerDraft(TimerEntry.of(it[0] as String), (it[1] as String).take(MAX_NOTE_LENGTH), it[2] as Boolean) },
        )
    }
}

/**
 * The tab at [now]: a card for each of [timers], oldest first, and the setter for [draft] when there are none or when
 * "New timer" has opened it. [ringing] and [waiting] are the ids of the timers the done screen has, or that wait for an
 * alarm; a timer that has ended shows that instead of counting.
 */
fun timersModel(
    timers: List<Timer>,
    ringing: Set<Long>,
    waiting: Set<Long>,
    now: Instant,
    zone: TimeZone,
    draft: TimerDraft = TimerDraft(),
): TimersModel {
    val cards = timers.map { timerCard(it, stateOf(it, ringing, waiting, now), now, zone) }
    val setterShown = cards.isEmpty() || draft.adding
    return TimersModel(
        sub = subtitle(cards),
        setter = if (setterShown) SetterModel(draft.entry, draft.note, canCancel = cards.isNotEmpty()) else null,
        cards = cards,
        showNewTimer = !setterShown,
    )
}

private fun stateOf(timer: Timer, ringing: Set<Long>, waiting: Set<Long>, now: Instant): TimerState = when {
    timer.id in ringing -> TimerState.RINGING
    timer.id in waiting -> TimerState.WAITING
    timer.hasEnded(now) -> TimerState.ENDED
    timer.running -> TimerState.RUNNING
    else -> TimerState.PAUSED
}

private fun timerCard(timer: Timer, state: TimerState, now: Instant, zone: TimeZone): TimerCard {
    val name = timer.name
    val summary = listOfNotNull(timer.note.takeIf { it.isNotBlank() }, totalText(timer.duration)).joinToString(" · ")
    val counting = state == TimerState.RUNNING || state == TimerState.PAUSED
    return TimerCard(
        id = timer.id,
        state = state,
        time = if (counting) clockText(timer.remaining(now)) else TIME_IS_UP,
        detail = when (state) {
            TimerState.RUNNING -> summary
            TimerState.PAUSED -> "Paused · $summary"
            TimerState.RINGING -> "Ringing now · $summary"
            TimerState.WAITING -> "Rings after your alarm · $summary"
            TimerState.ENDED -> "Ended at ${cityClock(requireNotNull(timer.endsAt), zone)} · $summary"
        },
        sky = timerSky(timer.progress(now)),
        toggleLabel = (if (state == TimerState.RUNNING) "Pause " else "Resume ") + name,
        addLabel = "Add a minute to $name",
        removeLabel = "Delete $name",
    )
}

private fun subtitle(cards: List<TimerCard>): String {
    if (cards.isEmpty()) return "Pip reads your note when one ends"
    val running = cards.count { it.state == TimerState.RUNNING }
    val paused = cards.count { it.state == TimerState.PAUSED }
    val ended = cards.size - running - paused
    return listOfNotNull(
        "$running running".takeIf { running > 0 },
        "$paused paused".takeIf { paused > 0 },
        "$ended ended".takeIf { ended > 0 },
    ).joinToString(" · ")
}

/** What Start asks for when the phone can't ring a timer on time: Android's exact alarms, or iOS's one permission. */
fun timerPermission(asked: List<AlarmPermission>): AlarmPermission =
    if (AlarmPermission.EXACT_ALARMS in asked) AlarmPermission.EXACT_ALARMS else AlarmPermission.ALARMS

internal const val TIME_IS_UP = "Time’s up"

private const val SHORT_CLOCK_LENGTH = 5

// The design's strip: noon to 19:20 over the whole timer, the sun crossing x 26 to 332 and dropping 44 from y 20.
private const val SKY_START_MINUTE = 12 * 60f
private const val SKY_SPAN_MINUTES = 440f
private const val SUN_LEFT = 26f
private const val SUN_SPAN = 306f
private const val SUN_TOP = 20f
private const val SUN_DROP = 44f
private const val STARS_FROM = 0.75f
private const val MAX_STAR_ALPHA = 0.8f
private const val SLEEPY_FROM = 0.85f
