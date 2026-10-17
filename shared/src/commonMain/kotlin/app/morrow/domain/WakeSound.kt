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
}

/** The alarm's sound, in picker order. All are bundled with the app, made by tools/make_sounds.py. */
enum class WakeSound(val displayName: String, val category: SoundCategory) {
    SOFT_PIANO("Soft piano", SoundCategory.GENTLE),
    WIND_CHIMES("Wind chimes", SoundCategory.GENTLE),
    MUSIC_BOX("Music box", SoundCategory.GENTLE),
    SINGING_BOWL("Singing bowl", SoundCategory.GENTLE),
    BIRDSONG("Birdsong", SoundCategory.NATURE),
    OCEAN_WAVES("Ocean waves", SoundCategory.NATURE),
    GENTLE_RAIN("Gentle rain", SoundCategory.NATURE),
    MARIMBA("Marimba", SoundCategory.UPBEAT),
    SUNRISE_ARPEGGIO("Sunrise synth", SoundCategory.UPBEAT),
    ALARM_BELL("Alarm bell", SoundCategory.CLASSIC),
    DIGITAL_BEEPS("Digital beeps", SoundCategory.CLASSIC),
}

/** How long the alarm takes to rise from quiet to full volume. */
enum class VolumeRamp(val displayName: String, val duration: Duration) {
    HALF_MINUTE("30 s", 30.seconds),
    ONE_MINUTE("1 min", 1.minutes),
    TWO_MINUTES("2 min", 2.minutes),
}
