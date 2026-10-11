package app.morrow.alarm

/**
 * AlarmKit, which rings alarms like the Clock app's, even with Morrow closed. It's Swift-only, so the app's Swift code
 * implements this and passes it to [app.morrow.IosApp.start]. Ids are Morrow's alarm ids.
 */
interface AlarmKitBridge {
    /** True once the user has allowed Morrow to set alarms. */
    val authorized: Boolean

    /** True until the user answers the permission prompt; after that only the Settings app can change it. */
    val canAsk: Boolean

    /** Shows the permission prompt, then reports on the main thread whether alarms are allowed. */
    fun requestAuthorization(onResult: (Boolean) -> Unit)

    /**
     * Rings at [hour]:[minute] showing [label] and playing [sound], a file in the app bundle. With [isoDays] (1 for Monday
     * to 7 for Sunday) it repeats on those days; without, it rings once, at the next [hour]:[minute].
     * Replaces any alarm with the same [id].
     */
    fun schedule(id: Long, label: String, hour: Int, minute: Int, isoDays: List<Int>, sound: String)

    /**
     * Rings once at [epochSeconds] (seconds since 1970 UTC), whatever the phone's zone, showing [label] and playing
     * [sound]. [id] is one of [alarmId]'s dates, from [app.morrow.alarms.fixedDateId]; its "Start mission" button
     * wakes [alarmId]. Replaces any alarm with the same [id].
     */
    fun scheduleOnce(id: Long, alarmId: Long, label: String, epochSeconds: Long, sound: String)

    fun cancel(id: Long)

    /** The alarms AlarmKit holds, waiting or ringing, including ones it's still being handed. Dates count by their own ids. */
    fun scheduledIds(): List<Long>
}
