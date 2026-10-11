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

@Database(entities = [AlarmEntity::class, SavedCityEntity::class], version = 4)
@ConstructedBy(MorrowDatabaseConstructor::class)
abstract class MorrowDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun savedCityDao(): SavedCityDao
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
        .addMigrations(GamesMigration, CitiesMigration, ZonesMigration)
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
        // Ids of deleted alarms stay used, as before: the copy would otherwise restart the count at the highest id left.
        connection.execSQL(
            "UPDATE sqlite_sequence SET `seq` = (SELECT MAX(`seq`) FROM sqlite_sequence WHERE `name` IN ('alarms', 'alarms_new')) " +
                "WHERE `name` = 'alarms_new'",
        )
        connection.execSQL(
            "INSERT INTO sqlite_sequence (`name`, `seq`) SELECT 'alarms_new', `seq` FROM sqlite_sequence WHERE `name` = 'alarms' " +
                "AND NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE `name` = 'alarms_new')",
        )
        connection.execSQL("DROP TABLE `alarms`")
        connection.execSQL("ALTER TABLE `alarms_new` RENAME TO `alarms`")
    }
}

/** Adds the World tab's cities. The table starts empty; CityRepository saves the phone's city as home. */
internal object CitiesMigration : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_cities` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `region` TEXT, " +
                "`country` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `time_zone` TEXT NOT NULL, " +
                "`population` INTEGER NOT NULL, `is_home` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** Alarms can keep another city's clock. Every alarm so far follows the phone's. */
internal object ZonesMigration : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `alarms` ADD COLUMN `time_zone` TEXT")
        connection.execSQL("ALTER TABLE `alarms` ADD COLUMN `zone_label` TEXT")
    }
}
