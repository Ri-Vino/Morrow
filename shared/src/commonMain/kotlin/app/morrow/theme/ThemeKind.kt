package app.morrow.theme

/** The four time-of-day themes. Midnight uses [NIGHT] too; only the greeting changes. */
enum class ThemeKind(val displayName: String) {
    MORNING("Morning light"),
    NOON("High noon"),
    AFTERNOON("Golden afternoon"),
    NIGHT("Moonlit night");

    companion object {
        /** Minutes after midnight at which a new theme starts. */
        internal val boundaries = listOf(5 * 60, 11 * 60, 15 * 60, 19 * 60)

        fun at(minuteOfDay: Int): ThemeKind = when {
            minuteOfDay < 5 * 60 -> NIGHT
            minuteOfDay < 11 * 60 -> MORNING
            minuteOfDay < 15 * 60 -> NOON
            minuteOfDay < 19 * 60 -> AFTERNOON
            else -> NIGHT
        }
    }
}
