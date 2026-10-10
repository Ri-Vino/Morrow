package app.morrow.domain

/** How hard each round of a game is. The number of rounds is set on each alarm. */
enum class Difficulty(val displayName: String, val shakeForce: Float) {
    GENTLE("Gentle", shakeForce = 1.7f),
    NORMAL("Normal", shakeForce = 2.0f),
    TOUGH("Tough", shakeForce = 2.4f),
}
