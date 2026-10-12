package app.morrow.alarm

/**
 * AlarmKit, which rings alarms like the Clock app's, even with Morrow closed. It's Swift-only, so the app's Swift code
 * implements this and passes it to [app.morrow.IosApp.start]. Ids are Morrow's alarm ids.
 *
 * AlarmKit documents no fixed limit on how many alarms an app may hold: the phone may refuse more depending on its
 * state, and scheduling then fails with `maximumLimitReached`. Each alarm on another city's clock takes up to
 * [app.morrow.alarms.FIXED_DATE_SLOTS] of them, and each timer one.
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

    /**
     * Counts down [seconds], showing [title] on the Lock Screen, then rings showing [title] and playing [sound] until
     * it's stopped. [id] is timer [timerId]'s id in AlarmKit, from [app.morrow.timers.timerAlarmKitId]; the alert's
     * "Open" button opens Morrow on that timer. Replaces any alarm with the same [id], so it also resumes a paused
     * countdown, or changes how long is left.
     */
    fun startCountdown(id: Long, timerId: Long, title: String, seconds: Double, sound: String)

    /**
     * Holds the countdown [id], paused with [seconds] left, as [startCountdown] would have counted it. A countdown that
     * isn't running, because it was lost or was paused with a different time left, is put back first.
     */
    fun pauseCountdown(id: Long, timerId: Long, title: String, seconds: Double, sound: String)

    /** Cancels an alarm, a date or a countdown. */
    fun cancel(id: Long)

    /**
     * What AlarmKit holds, waiting, counting down, paused or ringing, including what it's still being handed. Dates and
     * countdowns count by their own ids.
     */
    fun scheduledIds(): List<Long>

    /** Calls [onChange] on the main thread whenever what AlarmKit holds changes, as when a timer starts or stops ringing. */
    fun watch(onChange: () -> Unit)
}
