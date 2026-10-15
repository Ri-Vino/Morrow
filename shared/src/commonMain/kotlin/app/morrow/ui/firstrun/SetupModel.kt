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

fun setupModel(asked: List<AlarmPermission>, missing: List<AlarmPermission>): SetupModel {
    val total = asked.size
    val granted = total - missing.size
    val ready = missing.none { it.required }
    val required = asked.count { it.required }
    val optional = missing.filterNot { it.required }
    return SetupModel(
        readyText = "$granted of $total ready",
        progress = granted.toFloat() / total,
        ready = ready,
        note = when {
            !ready && required == 1 -> "Alarms can’t ring until this is allowed."
            !ready -> "Alarms can’t ring until the first ${Counts.getOrElse(required) { "$required" }} are allowed."
            optional.toSet() == setOf(AlarmPermission.BATTERY, AlarmPermission.LOCATION) ->
                "All set. The last two are optional: battery helps on Samsung and Xiaomi, and location gives you local weather."
            optional.size > 1 -> "All set. The rest are optional, and each one helps."
            AlarmPermission.OVER_APPS in missing -> "All set. Showing over other apps is optional, but fills the screen while you use your phone."
            AlarmPermission.BATTERY in missing -> "All set. Battery is optional, but helps on Samsung and Xiaomi."
            AlarmPermission.LOCATION in missing -> "All set. Location is optional, and gives you local weather."
            else -> "All set. Pip is ready when you are."
        },
    )
}

val AlarmPermission.description: String
    get() = when (this) {
        AlarmPermission.NOTIFICATIONS -> "Shows the alarm on your lock screen."
        AlarmPermission.EXACT_ALARMS -> "Rings on the minute, not roughly."
        AlarmPermission.FULL_SCREEN -> "Lets the sunrise fill your screen."
        AlarmPermission.OVER_APPS -> "Optional. Fills the screen even while you’re using your phone."
        AlarmPermission.BATTERY -> "Optional. Stops some phones putting Pip to sleep."
        AlarmPermission.LOCATION -> "Optional. For your local weather; a city works too."
        AlarmPermission.ALARMS -> "Lets Pip ring on time, even with Morrow closed."
    }

private val Counts = listOf("none", "one", "two", "three", "four")
