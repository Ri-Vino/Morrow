package app.morrow.ui.components

import app.morrow.weather.Sky

/** The design's line icon for [sky], for [MorrowIcon]. A clear sky at [night] gets the moon. */
fun weatherIcon(sky: Sky, night: Boolean): String = when (sky) {
    Sky.CLEAR -> if (night) Icons.Moon else Icons.Sun
    Sky.PARTLY_CLOUDY -> PARTLY
    Sky.CLOUDY -> CLOUD
    Sky.FOG -> FOG
    Sky.DRIZZLE -> CLOUD_TOP + "M8 18v.01M12 19v.01M16 18v.01"
    Sky.RAIN -> CLOUD_TOP + "M8.5 17L7 20.5M12.5 17L11 20.5M16.5 17L15 20.5"
    Sky.STORM -> CLOUD_TOP + "M12.5 15.5L10.5 19h3l-2 3.5"
    Sky.SNOW -> CLOUD_TOP + "M8 18h.01M12 18h.01M16 18h.01M10 21h.01M14 21h.01"
}

private const val CLOUD_TOP = "M7 14h10.5a3.6 3.6 0 0 0 .4-7.2A5.5 5.5 0 0 0 7.1 7.1 3.5 3.5 0 0 0 7 14z"
private const val PARTLY = "M7.5 3v1.5M3.4 4.9l1 1M2 9h1.5M11.6 4.9l-1 1M4.8 11.4a3.5 3.5 0 0 1 5.6-4.8" +
    "M9 20h8.5a3.5 3.5 0 0 0 .4-7A5 5 0 0 0 8.4 12.6 3.7 3.7 0 0 0 9 20z"
private const val CLOUD = "M7 18.5h10.5a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.4 10.9 3.8 3.8 0 0 0 7 18.5z"
private const val FOG = "M4 9h16M3 13h18M5 17h14M8 5h8"
