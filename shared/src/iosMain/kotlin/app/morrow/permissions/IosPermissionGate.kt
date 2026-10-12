package app.morrow.permissions

import app.morrow.alarm.AlarmKitBridge
import app.morrow.platform.AlarmPermission
import app.morrow.platform.IosLocationProvider
import app.morrow.platform.IosPermissions
import app.morrow.platform.PermissionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/** AlarmKit's permission, which alarms and timers need, and location "while using", which the weather may use. */
class IosPermissionGate(private val alarmKit: AlarmKitBridge, private val location: IosLocationProvider) : PermissionGate {

    override val asked: List<AlarmPermission> = IosPermissions
    private val state = MutableStateFlow(check())
    override val missing: StateFlow<List<AlarmPermission>> = state.asStateFlow()

    init {
        location.onAuthorizationChange = ::refresh
    }

    override fun refresh() {
        state.value = check()
    }

    override fun request(permission: AlarmPermission) {
        when (permission) {
            AlarmPermission.ALARMS -> if (alarmKit.canAsk) alarmKit.requestAuthorization { refresh() } else openAppSettings()
            // Location stays optional, so once it has been answered, the Allow button opens Settings to change it.
            AlarmPermission.LOCATION -> if (location.canAsk) location.requestWhenInUse() else openAppSettings()
            else -> Unit
        }
    }

    private fun check(): List<AlarmPermission> = buildList {
        if (!alarmKit.authorized) add(AlarmPermission.ALARMS)
        if (!location.allowed) add(AlarmPermission.LOCATION)
    }
}

/** Morrow's page in the Settings app, where a permission that was refused can be allowed. */
internal fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
}
