package app.morrow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.morrow.domain.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.time.Instant

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

    /** The one small thing to look forward to, from the Morning screen. It lasts until the next alarm rings. */
    val intention: Flow<String?> = store.data.map { it[Intention] }

    suspend fun setIntention(text: String) {
        store.edit { it[Intention] = text }
    }

    suspend fun clearIntention() {
        store.edit { it.remove(Intention) }
    }

    /** When the app was last left, as a rough bedtime for the Morning screen's sleep summary. */
    val lastClosedAt: Flow<Instant?> = store.data.map { prefs -> prefs[LastClosedAt]?.let(Instant::fromEpochMilliseconds) }

    suspend fun markClosed(at: Instant) {
        store.edit { it[LastClosedAt] = at.toEpochMilliseconds() }
    }

    private companion object {
        val FirstRunDone = booleanPreferencesKey("first_run_done")
        val Intention = stringPreferencesKey("intention")
        val LastClosedAt = longPreferencesKey("last_closed_at")
        val DifficultyKey = stringPreferencesKey("difficulty")
        val KettleCode = stringPreferencesKey("kettle_code")
    }
}

data class MissionSetup(val difficulty: Difficulty, val kettleCode: String?)

internal const val SETTINGS_FILE = "morrow.preferences_pb"

internal fun createSettingsStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
