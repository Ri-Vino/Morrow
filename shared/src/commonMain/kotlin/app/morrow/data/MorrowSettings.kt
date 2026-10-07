package app.morrow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.morrow.domain.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** Small app settings, kept in a DataStore file. Create one per app: DataStore allows a single instance per file. */
class MorrowSettings internal constructor(private val store: DataStore<Preferences>) {

    val firstRunDone: Flow<Boolean> = store.data.map { it[FirstRunDone] ?: false }

    suspend fun markFirstRunDone() {
        store.edit { it[FirstRunDone] = true }
    }

    /** What missions need at ring time. */
    val missionSetup: Flow<MissionSetup> = store.data.map { prefs ->
        MissionSetup(
            difficulty = prefs[DifficultyKey]?.let { name -> Difficulty.entries.firstOrNull { it.name == name } } ?: Difficulty.NORMAL,
            kettleCode = prefs[KettleCode],
        )
    }

    suspend fun setDifficulty(difficulty: Difficulty) {
        store.edit { it[DifficultyKey] = difficulty.name }
    }

    /** The code the Scan mission looks for, usually a sticker on the kettle. */
    suspend fun setKettleCode(code: String) {
        store.edit { it[KettleCode] = code }
    }

    private companion object {
        val FirstRunDone = booleanPreferencesKey("first_run_done")
        val DifficultyKey = stringPreferencesKey("difficulty")
        val KettleCode = stringPreferencesKey("kettle_code")
    }
}

data class MissionSetup(val difficulty: Difficulty, val kettleCode: String?)

internal const val SETTINGS_FILE = "morrow.preferences_pb"

internal fun createSettingsStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
