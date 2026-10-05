package app.morrow.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Lives in device-protected storage so alarms can be read and rescheduled after a reboot,
 * before the phone has been unlocked.
 */
fun morrowDatabaseBuilder(context: Context): RoomDatabase.Builder<MorrowDatabase> {
    val storage = context.applicationContext.createDeviceProtectedStorageContext()
    return Room.databaseBuilder<MorrowDatabase>(
        context = storage,
        name = storage.getDatabasePath(DATABASE_NAME).absolutePath,
    )
}
