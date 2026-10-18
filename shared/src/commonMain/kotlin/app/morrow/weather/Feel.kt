package app.morrow.weather

/** How warm it feels, coldest first, for Home's frost or heat haze and what Pip wears. Mild shows nothing extra. */
enum class Warmth { FREEZING, COLD, MILD, HOT, SCORCHING }

/** How the weather feels outside: how warm, and whether it's [windy]. */
data class Feel(val warmth: Warmth, val windy: Boolean) {
    companion object {
        val Mild = Feel(Warmth.MILD, windy = false)
    }
}

/**
 * How [celsius] and a [wind] in km/h feel. The bands meet halfway between whole degrees, so a reading always lands in
 * the band its rounded temperature on screen says: 0° and below is freezing, 1 to 7° cold, 27 to 32° hot, 33° and up
 * scorching. Unknown wind isn't windy.
 */
fun feelOf(celsius: Double, wind: Double?): Feel = Feel(
    warmth = when {
        celsius < 0.5 -> Warmth.FREEZING
        celsius < 7.5 -> Warmth.COLD
        celsius < 26.5 -> Warmth.MILD
        celsius < 32.5 -> Warmth.HOT
        else -> Warmth.SCORCHING
    },
    windy = wind != null && wind >= WINDY_KMH,
)

/** A fresh breeze on the Beaufort scale, enough to blow leaves about. */
const val WINDY_KMH = 30.0
