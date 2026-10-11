package app.morrow.weather

/** Where forecasts come from. Open-Meteo for now; switching provider before a paid release is one new class. */
interface WeatherSource {
    /**
     * Forecasts for [places] in a single request, one per distinct place after rounding, in first-seen order.
     * Match them up by [Forecast.place]. An empty list makes no request.
     * Throws [FetchException] for anything that stops a forecast arriving.
     */
    suspend fun forecast(places: List<Coordinates>): List<Forecast>
}

/**
 * A forecast or search that didn't arrive: offline, a server error or a reply that can't be read.
 * Callers catch it, keep what they have cached, and show nothing. When [rateLimited], wait until [backoffUntil].
 */
class FetchException(message: String, val rateLimited: Boolean = false, cause: Throwable? = null) : Exception(message, cause)
