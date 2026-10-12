package app.morrow.ui.components

enum class PipMood { ASLEEP, OPEN, HAPPY, SHADES, YAWNING, DELIGHTED }

/** How Pip looks. [moon] is Pip's night-time form. While [talking], his mouth opens and closes in place of the mood's own. */
data class PipLook(
    val mood: PipMood,
    val moon: Boolean = false,
    val nightcap: Boolean = false,
    val rays: Boolean = false,
    val glow: Boolean = true,
    val talking: Boolean = false,
)
