package app.morrow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.morrow.domain.Difficulty
import app.morrow.domain.VolumeRamp
import app.morrow.domain.WakeSound
import app.morrow.theme.ThemeKind
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
            difficulty = prefs[DifficultyKey].toEnum(Difficulty.NORMAL),
            kettleCode = prefs[KettleCode],
        )
    }

    /** What the alarm sounds like, needed at ring time. */
    val alarmSetup: Flow<AlarmSetup> = store.data.map { prefs ->
        AlarmSetup(
            sound = prefs[SoundKey].toEnum(WakeSound.BIRDSONG),
            ramp = prefs[RampKey].toEnum(VolumeRamp.ONE_MINUTE),
        )
    }

    suspend fun setSound(sound: WakeSound) {
        store.edit { it[SoundKey] = sound.name }
    }

    suspend fun setRamp(ramp: VolumeRamp) {
        store.edit { it[RampKey] = ramp.name }
    }

    /** A theme to keep whatever the time, or null to follow the clock. */
    val pinnedTheme: Flow<ThemeKind?> = store.data.map { prefs -> prefs[PinnedTheme]?.let { name -> ThemeKind.entries.firstOrNull { it.name == name } } }

    suspend fun setPinnedTheme(theme: ThemeKind?) {
        store.edit { if (theme == null) it.remove(PinnedTheme) else it[PinnedTheme] = theme.name }
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
        val SoundKey = stringPreferencesKey("sound")
        val RampKey = stringPreferencesKey("volume_ramp")
        val PinnedTheme = stringPreferencesKey("pinned_theme")
        val KettleCode = stringPreferencesKey("kettle_code")
    }
}

data class MissionSetup(val difficulty: Difficulty, val kettleCode: String?)

data class AlarmSetup(val sound: WakeSound, val ramp: VolumeRamp) {
    companion object {
        val Default = AlarmSetup(WakeSound.BIRDSONG, VolumeRamp.ONE_MINUTE)
    }
}

/** The stored name of one of [E]'s entries, or [default] if it's missing or no longer exists. */
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default

internal const val SETTINGS_FILE = "morrow.preferences_pb"

internal fun createSettingsStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
