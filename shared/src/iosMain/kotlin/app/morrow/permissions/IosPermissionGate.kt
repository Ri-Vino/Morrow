package app.morrow.permissions

import app.morrow.alarm.AlarmKitBridge
import app.morrow.platform.AlarmPermission
import app.morrow.platform.IosPermissions
import app.morrow.platform.PermissionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

class IosPermissionGate(private val alarmKit: AlarmKitBridge) : PermissionGate {

    override val asked: List<AlarmPermission> = IosPermissions
    private val state = MutableStateFlow(check())
    override val missing: StateFlow<List<AlarmPermission>> = state.asStateFlow()

    override fun refresh() {
        state.value = check()
    }

    override fun request(permission: AlarmPermission) {
        if (permission != AlarmPermission.ALARMS) return
        if (alarmKit.canAsk) alarmKit.requestAuthorization { refresh() } else openAppSettings()
    }

    private fun check(): List<AlarmPermission> = if (alarmKit.authorized) emptyList() else IosPermissions
}

/** Morrow's page in the Settings app, where a permission that was refused can be allowed. */
internal fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
}
