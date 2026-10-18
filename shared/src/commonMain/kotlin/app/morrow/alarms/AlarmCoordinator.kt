package app.morrow.alarms

import app.morrow.data.AlarmRepository
import app.morrow.domain.Alarm
import app.morrow.domain.nextRing
import app.morrow.platform.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Keeps the OS schedule in step with the stored alarms. */
class AlarmCoordinator(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock = Clock.System,
    /** The phone's zone. Alarms on another city's clock keep to that city's zone instead. */
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

    private val doneWith = MutableStateFlow<Alarm?>(null)

    /** The temporary alarm that last deleted itself, switched off, so Home can offer to put it back. */
    val deletedWhenDone: StateFlow<Alarm?> = doneWith.asStateFlow()

    /** Home has offered [deletedWhenDone] back, so it needn't again. */
    fun forgetDeletedWhenDone() {
        doneWith.value = null
    }

    /**
     * The alarm [id] has rung and is done with: its games are over, its safety stop ended it, or its snooze was called
     * off. A temporary one deletes itself; any other is left as it is. Not for a snooze, after which it rings again.
     */
    suspend fun onFinished(id: Long) {
        val alarm = repository.get(id) ?: return
        if (!alarm.temporary) return
        delete(id)
        // Off, so putting it back can't set it ringing again tomorrow at the same time.
        doneWith.value = alarm.copy(enabled = false)
    }

    /** After a reboot, a clock or timezone change, an app update or a permission change. */
    suspend fun rescheduleAll() {
        repository.getAll().forEach(::sync)
    }

    /**
     * For iOS, where the system rings and stops alarms without waking the app. Given the ids the system still holds,
     * turns off one-off alarms that have rung since, deletes temporary ones, and puts back repeating ones it has lost.
     * Repeating alarms on another city's clock are handed over again each time, since the system holds only their
     * next week of dates.
     */
    suspend fun catchUp(stillScheduled: Set<Long>) {
        repository.getAll()
            // A zoned alarm still holding any of its dates is left alone: handing it over again would cancel the date
            // that may be ringing as the app opens. It's topped up once its dates run out, and after each mission.
            .filter { it.enabled && it.id !in stillScheduled }
            .forEach {
                when {
                    it.repeats -> sync(it)
                    it.temporary -> onFinished(it.id)
                    else -> repository.setEnabled(it.id, false)
                }
            }
    }

    suspend fun onRang(id: Long) {
        val alarm = repository.get(id) ?: return
        if (alarm.repeats) {
            // A minute's margin so an alarm delivered a moment early isn't scheduled for the same minute again.
            val after = clock.now() + 1.minutes
            scheduler.schedule(alarm, alarm.nextRing(after, timeZone()))
        } else {
            setEnabled(id, false)
        }
    }

    private fun sync(alarm: Alarm) {
        if (alarm.enabled) {
            scheduler.schedule(alarm, alarm.nextRing(clock.now(), timeZone()))
        } else {
            scheduler.cancel(alarm.id)
        }
    }
}
