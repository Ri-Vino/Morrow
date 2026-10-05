package app.morrow.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(entities = [AlarmEntity::class], version = 1)
@ConstructedBy(MorrowDatabaseConstructor::class)
abstract class MorrowDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}

// Room generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object MorrowDatabaseConstructor : RoomDatabaseConstructor<MorrowDatabase> {
    override fun initialize(): MorrowDatabase
}

internal const val DATABASE_NAME = "morrow.db"

fun buildMorrowDatabase(builder: RoomDatabase.Builder<MorrowDatabase>): MorrowDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
