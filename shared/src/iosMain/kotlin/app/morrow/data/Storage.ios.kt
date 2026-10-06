package app.morrow.data

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun morrowDatabaseBuilder(): RoomDatabase.Builder<MorrowDatabase> {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return Room.databaseBuilder<MorrowDatabase>(name = requireNotNull(documents?.path) + "/$DATABASE_NAME")
}

/** Create one per app. */
fun morrowSettings(): MorrowSettings = MorrowSettings(createSettingsStore(settingsPath()))

@OptIn(ExperimentalForeignApi::class)
private fun settingsPath(): String {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documents?.path) + "/$SETTINGS_FILE"
}
