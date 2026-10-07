package app.morrow.domain

/** How much a mission asks of you. Breathe is always three breaths. */
enum class Difficulty(val displayName: String, val sums: Int, val shakes: Int) {
    GENTLE("Gentle", sums = 2, shakes = 20),
    NORMAL("Normal", sums = 3, shakes = 30),
    TOUGH("Tough", sums = 5, shakes = 45),
}
