package app.morrow.alarm

import app.morrow.alarms.FIXED_DATE_SLOTS
import app.morrow.alarms.fixedDateId
import app.morrow.alarms.fixedDates
import app.morrow.domain.Alarm
import app.morrow.domain.WakeSound
import app.morrow.domain.ownZone
import app.morrow.platform.AlarmScheduler
import kotlinx.datetime.isoDayNumber
import kotlin.time.Instant

/**
 * Hands alarms to AlarmKit with their own sound, or the one chosen in Settings now, since AlarmKit plays the sound it
 * was given when scheduled.
 */
class IosAlarmScheduler(private val alarmKit: AlarmKitBridge, private val sound: () -> WakeSound) : AlarmScheduler {

    override fun schedule(alarm: Alarm, at: Instant) {
        val zone = alarm.ownZone()
        if (zone == null) {
            // AlarmKit repeats alarms itself and follows the wall clock across timezones, so it takes the time of day, not [at].
            cancelDates(alarm.id)
            val days = alarm.days.map { it.isoDayNumber }.sorted()
            alarmKit.schedule(alarm.id, alarm.label, alarm.hour, alarm.minute, days, (alarm.sound ?: sound()).fileName)
        } else {
            // AlarmKit's repeats keep the phone's clock, so another city's alarm goes over as its next week of dates.
            alarmKit.cancel(alarm.id)
            cancelDates(alarm.id)
            val file = (alarm.sound ?: sound()).fileName
            // Up to FIXED_DATE_SLOTS alarms each. AlarmKit sets no fixed limit but may refuse more when the phone is
            // short of resources (maximumLimitReached); a refused date is only logged, and comes back when the alarm is next handed over.
            alarm.fixedDates(at, zone).forEachIndexed { i, date ->
                alarmKit.scheduleOnce(fixedDateId(alarm.id, i + 1), alarm.id, alarm.label, date.epochSeconds, file)
            }
        }
    }

    override fun cancel(alarmId: Long) {
        alarmKit.cancel(alarmId)
        cancelDates(alarmId)
    }

    private fun cancelDates(alarmId: Long) {
        for (slot in 1..FIXED_DATE_SLOTS) alarmKit.cancel(fixedDateId(alarmId, slot))
    }
}

/** The sound's file in the app bundle, made by tools/make_sounds.py. */
internal val WakeSound.fileName: String
    get() = "${name.lowercase()}.caf"
