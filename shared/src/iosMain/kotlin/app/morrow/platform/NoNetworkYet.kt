package app.morrow.platform

import app.morrow.weather.Coordinates
import kotlin.time.Duration

/**
 * iOS has no HTTP client yet: every request fails as if the phone were offline, so weather stays empty and city search
 * uses the bundled list. NSURLSession replaces it in Phase 22.
 */
internal object NoHttpClientYet : HttpClient {
    override suspend fun get(url: String): HttpResponse = throw HttpException("No network on iOS yet")
}

/** iOS has no location yet, so weather uses the home city. CLLocationManager replaces it in Phase 22. */
internal object NoLocationYet : LocationProvider {
    override val allowed: Boolean = false

    override suspend fun current(timeout: Duration): Coordinates? = null
}
