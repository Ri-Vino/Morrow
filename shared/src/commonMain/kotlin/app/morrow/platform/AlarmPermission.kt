package app.morrow.platform

import kotlinx.coroutines.flow.StateFlow

/** What must be allowed for an alarm to ring and show up properly. */
enum class AlarmPermission(val required: Boolean, val title: String) {
    NOTIFICATIONS(required = true, title = "Notifications"),
    EXACT_ALARMS(required = true, title = "Exact alarms"),
    FULL_SCREEN(required = true, title = "Full-screen alarm"),

    /** Only matters on phones that put background apps to sleep aggressively. */
    BATTERY(required = false, title = "Unrestricted battery"),
}

/** The platform's view of [AlarmPermission]s. */
interface PermissionGate {
    /** Permissions not granted yet, in [AlarmPermission] order. */
    val missing: StateFlow<List<AlarmPermission>>

    /** Re-reads every permission; call when the app comes back to the foreground. */
    fun refresh()

    /** Asks the system for [permission], or opens the settings screen where the user can allow it. */
    fun request(permission: AlarmPermission)
}
