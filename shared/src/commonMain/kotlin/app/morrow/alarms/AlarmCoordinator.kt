package app.morrow.alarms

import app.morrow.data.AlarmRepository
import app.morrow.domain.Alarm
import app.morrow.domain.nextOccurrence
import app.morrow.platform.AlarmScheduler
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Keeps the OS schedule in step with the stored alarms. */
class AlarmCoordinator(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    suspend fun save(alarm: Alarm): Long {
        val id = repository.save(alarm)
        sync(alarm.copy(id = id))
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        repository.setEnabled(id, enabled)
        repository.get(id)?.let(::sync)
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
    }

    /** After a reboot, a clock or timezone change, an app update or a permission change. */
    suspend fun rescheduleAll() {
        repository.getAll().forEach(::sync)
    }

    suspend fun onRang(id: Long) {
        val alarm = repository.get(id) ?: return
        if (alarm.repeats) {
            // A minute's margin so an alarm delivered a moment early isn't scheduled for the same minute again.
            val after = clock.now() + 1.minutes
            scheduler.schedule(alarm, alarm.nextOccurrence(after, timeZone()))
        } else {
            setEnabled(id, false)
        }
    }

    private fun sync(alarm: Alarm) {
        if (alarm.enabled) {
            scheduler.schedule(alarm, alarm.nextOccurrence(clock.now(), timeZone()))
        } else {
            scheduler.cancel(alarm.id)
        }
    }
}
