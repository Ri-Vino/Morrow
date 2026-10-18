package app.morrow.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How the sounds are grouped in the pickers. */
enum class SoundCategory(val displayName: String) {
    GENTLE("Gentle"),
    NATURE("Nature"),
    UPBEAT("Upbeat"),
    CLASSIC("Classic"),
    CLASSICAL("Classical"),
}

/**
 * The alarm's sound, in picker order. All are bundled with the app: most made by tools/make_sounds.py, the Classical
 * ones cut from public domain recordings by tools/clip_recordings.py.
 */
enum class WakeSound(val displayName: String, val category: SoundCategory) {
    SOFT_PIANO("Soft piano", SoundCategory.GENTLE),
    WIND_CHIMES("Wind chimes", SoundCategory.GENTLE),
    MUSIC_BOX("Music box", SoundCategory.GENTLE),
    SINGING_BOWL("Singing bowl", SoundCategory.GENTLE),
    KALIMBA("Kalimba", SoundCategory.GENTLE),
    MORNING_CHOIR("Morning choir", SoundCategory.GENTLE),
    BIRDSONG("Birdsong", SoundCategory.NATURE),
    OCEAN_WAVES("Ocean waves", SoundCategory.NATURE),
    GENTLE_RAIN("Gentle rain", SoundCategory.NATURE),
    CAMPFIRE("Campfire", SoundCategory.NATURE),
    MARIMBA("Marimba", SoundCategory.UPBEAT),
    SUNRISE_ARPEGGIO("Sunrise synth", SoundCategory.UPBEAT),
    STEEL_PAN("Steel pan", SoundCategory.UPBEAT),
    LOFI_KEYS("Lo-fi keys", SoundCategory.UPBEAT),
    ALARM_BELL("Alarm bell", SoundCategory.CLASSIC),
    DIGITAL_BEEPS("Digital beeps", SoundCategory.CLASSIC),
    CUCKOO_CLOCK("Cuckoo clock", SoundCategory.CLASSIC),
    MORNING_MOOD("Morning Mood", SoundCategory.CLASSICAL),
    GYMNOPEDIE("Gymnopédie No. 1", SoundCategory.CLASSICAL),
    GOLDBERG_ARIA("Goldberg Aria", SoundCategory.CLASSICAL),
    THE_LARK("The Lark", SoundCategory.CLASSICAL),
    FIGARO("Figaro overture", SoundCategory.CLASSICAL),
    ITALIAN_SYMPHONY("Italian Symphony", SoundCategory.CLASSICAL),
}

/** How long the alarm takes to rise from quiet to full volume. */
enum class VolumeRamp(val displayName: String, val duration: Duration) {
    HALF_MINUTE("30 s", 30.seconds),
    ONE_MINUTE("1 min", 1.minutes),
    TWO_MINUTES("2 min", 2.minutes),
}
