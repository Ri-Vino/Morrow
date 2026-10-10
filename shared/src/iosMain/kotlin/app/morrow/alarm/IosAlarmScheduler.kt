package app.morrow.alarm

import app.morrow.domain.Alarm
import app.morrow.domain.WakeSound
import app.morrow.platform.AlarmScheduler
import kotlinx.datetime.isoDayNumber
import kotlin.time.Instant

/** Hands alarms to AlarmKit with the sound chosen now, since AlarmKit plays the sound it was given when scheduled. */
class IosAlarmScheduler(private val alarmKit: AlarmKitBridge, private val sound: () -> WakeSound) : AlarmScheduler {

    // AlarmKit repeats alarms itself and follows the wall clock across timezones, so it takes the time of day, not [at].
    override fun schedule(alarm: Alarm, at: Instant) {
        val days = alarm.days.map { it.isoDayNumber }.sorted()
        alarmKit.schedule(alarm.id, alarm.label, alarm.hour, alarm.minute, days, sound().fileName)
    }

    override fun cancel(alarmId: Long) = alarmKit.cancel(alarmId)
}

/** The sound's file in the app bundle, made by tools/make_sounds.py. */
internal val WakeSound.fileName: String
    get() = when (this) {
        WakeSound.BIRDSONG -> "birdsong.caf"
        WakeSound.WIND_CHIMES -> "wind_chimes.caf"
        WakeSound.SOFT_PIANO -> "soft_piano.caf"
    }
