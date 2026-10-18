package app.morrow.weather

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A cache younger than this is used as it is. */
val RefreshAfter: Duration = 30.minutes

/** Even asked to refresh, a cache younger than this is kept: Open-Meteo's current weather changes every 15 minutes. */
val ManualRefreshAfter: Duration = 5.minutes

/** How long to leave Open-Meteo alone after it answers 429. */
val RateLimitBackoff: Duration = 1.hours

private val FreshFor = 3.hours
private val KeptFor = 24.hours

/** True when there's no cache, it's older than [after], or it's from the future because the clock moved back. */
fun needsRefresh(fetchedAt: Instant?, now: Instant, after: Duration = RefreshAfter): Boolean {
    if (fetchedAt == null) return true
    val age = now - fetchedAt
    return age > after || age.isNegative()
}

/** How old the weather is, which decides how it's labelled and whether temperatures show at all. */
sealed interface WeatherAge {
    /** Up to 3 hours: "updated N min ago". */
    data class Fresh(val minutes: Long) : WeatherAge

    /** 3 to 24 hours: "from N hours ago". */
    data class Old(val hours: Long) : WeatherAge

    /** Over 24 hours, or never fetched: no temperatures, and the sky shows the time of day only. */
    data object Expired : WeatherAge
}

fun weatherAge(fetchedAt: Instant?, now: Instant): WeatherAge {
    if (fetchedAt == null) return WeatherAge.Expired
    val age = (now - fetchedAt).coerceAtLeast(Duration.ZERO)
    return when {
        age <= FreshFor -> WeatherAge.Fresh(age.inWholeMinutes)
        age <= KeptFor -> WeatherAge.Old(age.inWholeHours)
        else -> WeatherAge.Expired
    }
}

/** When to try again after a 429. */
fun backoffUntil(now: Instant): Instant = now + RateLimitBackoff

/** Whether a 429's backoff still holds. */
// An end more than a backoff away was set before the clock moved back, so it doesn't count.
fun isBackingOff(until: Instant?, now: Instant): Boolean = until != null && now < until && until - now <= RateLimitBackoff

/** Settings › Weather: "Where I am" follows the phone's location; "Home city" always uses the home city. */
enum class WeatherPlaceMode { AUTO, HOME }

/**
 * The place whose weather Home shows: the [current] fix, then the [last] fix, then the [home] city, then the city
 * for the phone's time zone. In [WeatherPlaceMode.HOME] the fixes are skipped.
 */
fun weatherPlace(
    current: Coordinates?,
    last: Coordinates?,
    home: Coordinates?,
    zoneCity: Coordinates?,
    mode: WeatherPlaceMode = WeatherPlaceMode.AUTO,
): Coordinates? = when (mode) {
    WeatherPlaceMode.AUTO -> current ?: last ?: home ?: zoneCity
    WeatherPlaceMode.HOME -> home ?: zoneCity
}
