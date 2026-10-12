@file:OptIn(ExperimentalForeignApi::class)

package app.morrow.platform

import app.morrow.weather.Coordinates
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyReduced
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSinceNow
import platform.darwin.NSObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * A rough fix from CLLocationManager at reduced accuracy, asked for "while using" only, so never in the background.
 * Create it on the main thread, which CLLocationManager answers on.
 */
class IosLocationProvider : LocationProvider {

    private val listener = LocationListener(onFix = ::answered, onAuthorization = { onAuthorizationChange() })
    private val manager = CLLocationManager().apply {
        delegate = listener
        desiredAccuracy = kCLLocationAccuracyReduced
    }
    private var pending: CompletableDeferred<CLLocation?>? = null
    private var pendingSince = TimeSource.Monotonic.markNow()

    /** Told on the main thread when the user allows or refuses location, here or in the Settings app. */
    var onAuthorizationChange: () -> Unit = {}

    override val allowed: Boolean
        get() = manager.authorizationStatus.let { it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways }

    /** True until the user answers the prompt; after that only the Settings app can change it. */
    val canAsk: Boolean get() = manager.authorizationStatus == kCLAuthorizationStatusNotDetermined

    /** Shows the "while using" prompt; [onAuthorizationChange] hears the answer. */
    fun requestWhenInUse() = manager.requestWhenInUseAuthorization()

    override suspend fun current(timeout: Duration): Coordinates? = withContext(Dispatchers.Main) {
        if (!allowed) return@withContext null
        val fix = withTimeoutOrNull(timeout) { freshFix() } ?: recentFix()
        fix?.coordinate?.useContents { Coordinates(latitude, longitude).rounded() }
    }

    /** One fix. A request already on its way is shared, and outlives a caller that stops waiting for it. */
    private suspend fun freshFix(): CLLocation? {
        val waiting = pending?.takeIf { pendingSince.elapsedNow() < STUCK }
            ?: CompletableDeferred<CLLocation?>().also {
                pending = it
                pendingSince = TimeSource.Monotonic.markNow()
                manager.requestLocation()
            }
        return waiting.await()
    }

    /** The phone's last fix, if it's recent enough to still be roughly where the phone is. */
    private fun recentFix(): CLLocation? = manager.location?.takeIf { -it.timestamp.timeIntervalSinceNow < MAX_AGE.inWholeSeconds }

    private fun answered(location: CLLocation?) {
        pending?.complete(location)
        pending = null
    }

    private companion object {
        val MAX_AGE = 6.hours

        /** requestLocation always answers, but should it ever not, a later call asks again. */
        val STUCK = 1.minutes
    }
}

// CLLocationManager holds its delegate weakly, so the provider keeps it.
private class LocationListener(
    private val onFix: (CLLocation?) -> Unit,
    private val onAuthorization: () -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onFix(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onFix(null)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthorization()
    }
}
