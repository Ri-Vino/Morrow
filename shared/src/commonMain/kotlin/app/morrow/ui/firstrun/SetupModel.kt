package app.morrow.ui.firstrun

import app.morrow.platform.AlarmPermission

data class SetupModel(
    val readyText: String,
    /** 0 to 1, how far Pip has risen. */
    val progress: Float,
    /** Every required permission is allowed. */
    val ready: Boolean,
    val note: String,
)

fun setupModel(missing: List<AlarmPermission>): SetupModel {
    val total = AlarmPermission.entries.size
    val granted = total - missing.size
    val ready = missing.none { it.required }
    return SetupModel(
        readyText = "$granted of $total ready",
        progress = granted.toFloat() / total,
        ready = ready,
        note = when {
            !ready -> "Alarms can’t ring until the first three are allowed."
            AlarmPermission.BATTERY in missing -> "All set. Battery is optional, but helps on Samsung and Xiaomi."
            else -> "All set. Pip is ready when you are."
        },
    )
}

val AlarmPermission.description: String
    get() = when (this) {
        AlarmPermission.NOTIFICATIONS -> "Shows the alarm on your lock screen."
        AlarmPermission.EXACT_ALARMS -> "Rings on the minute, not roughly."
        AlarmPermission.FULL_SCREEN -> "Lets the sunrise fill your screen."
        AlarmPermission.BATTERY -> "Optional. Stops some phones putting Pip to sleep."
    }
