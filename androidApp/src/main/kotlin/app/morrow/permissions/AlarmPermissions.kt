package app.morrow.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions

class AlarmPermissions(private val context: Context) {

    fun missing(): List<AlarmPermission> = AndroidPermissions.filterNot(::isGranted)

    fun isGranted(permission: AlarmPermission): Boolean = when (permission) {
        AlarmPermission.NOTIFICATIONS -> notificationManager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        AlarmPermission.EXACT_ALARMS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        AlarmPermission.FULL_SCREEN -> Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
        AlarmPermission.OVER_APPS -> Settings.canDrawOverlays(context)
        // Only phones known to put background apps to sleep aggressively need this.
        AlarmPermission.BATTERY -> !hasAggressiveBatterySaver() ||
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        AlarmPermission.LOCATION -> context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        AlarmPermission.ALARMS -> true // iOS only.
    }

    /** The system screen where the user can allow [permission]. Location, and notifications on Android 13+, are requested at runtime first. */
    @SuppressLint("InlinedApi") // Each one can only be missing on the API level that introduced it.
    fun settingsIntent(permission: AlarmPermission): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (permission) {
            AlarmPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            AlarmPermission.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            AlarmPermission.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
            AlarmPermission.OVER_APPS -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            AlarmPermission.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            AlarmPermission.LOCATION, AlarmPermission.ALARMS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
    }

    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    private fun hasAggressiveBatterySaver() = Build.MANUFACTURER.lowercase() in setOf("samsung", "xiaomi", "redmi", "poco")
}
