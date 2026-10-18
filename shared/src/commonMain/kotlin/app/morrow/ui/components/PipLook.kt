package app.morrow.ui.components

import app.morrow.theme.Holiday

enum class PipMood { ASLEEP, OPEN, HAPPY, SHADES, YAWNING, DELIGHTED }

/**
 * How Pip looks. [moon] is Pip's night-time form. While [talking], his mouth opens and closes in place of the mood's own.
 * [outside] is what he wears and does for the weather, and [holiday] his costume, whose hat takes the place of any other.
 */
data class PipLook(
    val mood: PipMood,
    val moon: Boolean = false,
    val nightcap: Boolean = false,
    val rays: Boolean = false,
    val glow: Boolean = true,
    val talking: Boolean = false,
    val outside: PipOutside = PipOutside(),
    val holiday: Holiday? = null,
)

/** What Pip wears and does for the weather outside. All off is a mild, still day. */
data class PipOutside(
    val scarf: Boolean = false,
    /** A woolly hat, unless he's already in his nightcap. */
    val beanie: Boolean = false,
    /** How often he shivers, in seconds; 0 for never. */
    val shiverEvery: Float = 0f,
    /** Puffs of breath in the cold air. */
    val breath: Boolean = false,
    val sweat: Boolean = false,
    /** Fans himself with a little paper fan. */
    val fan: Boolean = false,
    /** Leans into the wind and squints, his scarf blowing out behind. */
    val windswept: Boolean = false,
)
