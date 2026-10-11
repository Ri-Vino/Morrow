package app.morrow.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.morrow.weather.Coordinates
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * A coarse fix from the phone's network location, through LocationManagerCompat rather than Google Play Services.
 * Only ever asked while the app is open; there's no background location.
 */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(LocationManager::class.java)

    override val allowed: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun current(timeout: Duration): Coordinates? {
        if (!allowed || manager == null || !LocationManagerCompat.isLocationEnabled(manager)) return null
        return try {
            val fix = withTimeoutOrNull(timeout) { freshFix(manager) } ?: recentFix(manager)
            fix?.let { Coordinates(it.latitude, it.longitude).rounded() }
        } catch (_: SecurityException) {
            // Permission taken away between the check and the request.
            null
        }
    }

    @SuppressLint("MissingPermission") // Only called from current(), which checks first and catches SecurityException.
    private suspend fun freshFix(manager: LocationManager): Location? {
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                LocationManager.NETWORK_PROVIDER,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { location -> continuation.resume(location) }
        }
    }

    /** The phone's last fix from any app, if it's recent enough to still be roughly where the phone is. */
    @SuppressLint("MissingPermission") // As for freshFix.
    private fun recentFix(manager: LocationManager): Location? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { provider -> manager.getLastKnownLocation(provider)?.takeIf { it.isRecent() } }

    private fun Location.isRecent(): Boolean = System.currentTimeMillis() - time < MAX_AGE.inWholeMilliseconds

    private companion object {
        val MAX_AGE = 6.hours
    }
}
