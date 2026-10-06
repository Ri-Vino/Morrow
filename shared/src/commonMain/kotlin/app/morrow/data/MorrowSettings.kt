package app.morrow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** Small app settings, kept in a DataStore file. Create one per app: DataStore allows a single instance per file. */
class MorrowSettings internal constructor(private val store: DataStore<Preferences>) {

    val firstRunDone: Flow<Boolean> = store.data.map { it[FirstRunDone] ?: false }

    suspend fun markFirstRunDone() {
        store.edit { it[FirstRunDone] = true }
    }

    private companion object {
        val FirstRunDone = booleanPreferencesKey("first_run_done")
    }
}

internal const val SETTINGS_FILE = "morrow.preferences_pb"

internal fun createSettingsStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
