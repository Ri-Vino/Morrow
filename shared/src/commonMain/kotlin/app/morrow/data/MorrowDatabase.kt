package app.morrow.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(entities = [AlarmEntity::class], version = 2)
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
        .addMigrations(GamesMigration)
        .build()

/** An alarm's one mission becomes its first and only game, with that game's usual rounds. */
internal object GamesMigration : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE `alarms_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `days` INTEGER NOT NULL, `games` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL)",
        )
        connection.execSQL(
            "INSERT INTO `alarms_new` (`id`, `hour`, `minute`, `label`, `days`, `games`, `enabled`) " +
                "SELECT `id`, `hour`, `minute`, `label`, `days`, " +
                "CASE `mission` WHEN 'BREATHE' THEN 'BREATHE:3' WHEN 'SHAKE' THEN 'SHAKE:3' WHEN 'SCAN' THEN 'SCAN:1' " +
                "ELSE 'SUMS:3' END, `enabled` FROM `alarms`",
        )
        connection.execSQL("DROP TABLE `alarms`")
        connection.execSQL("ALTER TABLE `alarms_new` RENAME TO `alarms`")
    }
}
