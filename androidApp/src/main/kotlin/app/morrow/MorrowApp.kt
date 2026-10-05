package app.morrow

import android.app.Application
import android.content.Context
import app.morrow.alarm.AndroidAlarmScheduler
import app.morrow.alarms.AlarmCoordinator
import app.morrow.data.AlarmRepository
import app.morrow.data.buildMorrowDatabase
import app.morrow.data.morrowDatabaseBuilder

class MorrowApp : Application() {
    private val database by lazy { buildMorrowDatabase(morrowDatabaseBuilder(this)) }
    val repository by lazy { AlarmRepository(database.alarmDao()) }
    val coordinator by lazy { AlarmCoordinator(repository, AndroidAlarmScheduler(this)) }
}

val Context.morrow: MorrowApp get() = applicationContext as MorrowApp
