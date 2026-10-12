package app.morrow.timers

import app.morrow.data.TimerDao
import app.morrow.data.toEntity
import app.morrow.data.toTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The stored timers. Use [TimerCoordinator] to change them, so the OS schedule and notifications follow. */
class TimerRepository(private val dao: TimerDao) {

    /** Every timer, oldest first. */
    val timers: Flow<List<Timer>> = dao.observeAll().map { rows -> rows.map { it.toTimer() } }

    suspend fun getAll(): List<Timer> = dao.getAll().map { it.toTimer() }

    suspend fun get(id: Long): Timer? = dao.get(id)?.toTimer()

    /** Inserts when [Timer.id] is 0, otherwise updates. Returns the timer's id. */
    suspend fun save(timer: Timer): Long {
        val rowId = dao.upsert(timer.toEntity())
        return if (timer.id == 0L) rowId else timer.id
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
