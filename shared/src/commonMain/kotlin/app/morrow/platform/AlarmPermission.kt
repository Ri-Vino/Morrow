package app.morrow.platform

import kotlinx.coroutines.flow.StateFlow

/** What must be allowed for an alarm to ring and show up properly, and the optional extras. */
enum class AlarmPermission(val required: Boolean, val title: String) {
    NOTIFICATIONS(required = true, title = "Notifications"),
    EXACT_ALARMS(required = true, title = "Exact alarms"),
    FULL_SCREEN(required = true, title = "Full-screen alarm"),

    /** Only matters on phones that put background apps to sleep aggressively. */
    BATTERY(required = false, title = "Unrestricted battery"),

    /** Coarse location, for "Where I am" weather. Without it, weather uses the home city. */
    LOCATION(required = false, title = "Location"),

    /** iOS's single alarm permission, from AlarmKit, which covers what the Android ones do. */
    ALARMS(required = true, title = "Alarms"),
}

/** The permissions Android asks for, in the order they're shown. */
val AndroidPermissions = listOf(
    AlarmPermission.NOTIFICATIONS,
    AlarmPermission.EXACT_ALARMS,
    AlarmPermission.FULL_SCREEN,
    AlarmPermission.BATTERY,
    AlarmPermission.LOCATION,
)

/** The one permission iOS asks for. */
val IosPermissions = listOf(AlarmPermission.ALARMS)

/** The platform's view of [AlarmPermission]s. */
interface PermissionGate {
    /** The permissions this platform asks for, in the order they're shown. */
    val asked: List<AlarmPermission>

    /** Permissions from [asked] not granted yet, in the same order. */
    val missing: StateFlow<List<AlarmPermission>>

    /** Re-reads every permission; call when the app comes back to the foreground. */
    fun refresh()

    /** Asks the system for [permission], or opens the settings screen where the user can allow it. */
    fun request(permission: AlarmPermission)
}
