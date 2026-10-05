package app.morrow.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun morrowDatabaseBuilder(context: Context): RoomDatabase.Builder<MorrowDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<MorrowDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DATABASE_NAME).absolutePath,
    )
}
