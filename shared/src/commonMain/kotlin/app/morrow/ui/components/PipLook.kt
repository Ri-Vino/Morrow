package app.morrow.ui.components

enum class PipMood { ASLEEP, OPEN, HAPPY, SHADES, YAWNING }

/** How Pip looks. [moon] is Pip's night-time form. */
data class PipLook(
    val mood: PipMood,
    val moon: Boolean = false,
    val nightcap: Boolean = false,
    val rays: Boolean = false,
    val glow: Boolean = true,
)
