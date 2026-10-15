package app.morrow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.morrow.domain.Difficulty
import app.morrow.domain.VolumeRamp
import app.morrow.domain.WakeSound
import app.morrow.weather.Coordinates
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode
import app.morrow.weather.defaultUnits
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

    /** Weather on means network for forecasts and city search, and location if allowed. Off means none of it. */
    val weatherEnabled: Flow<Boolean> = store.data.map { it[WeatherEnabled] ?: true }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        store.edit { it[WeatherEnabled] = enabled }
    }

    /** "Where I am" or "Home city". */
    val weatherSource: Flow<WeatherPlaceMode> = store.data.map { it[WeatherSourceKey].toEnum(WeatherPlaceMode.AUTO) }

    suspend fun setWeatherSource(mode: WeatherPlaceMode) {
        store.edit { it[WeatherSourceKey] = mode.name }
    }

    /** The units the user picked, or null to follow their country; see [effectiveUnits]. */
    val units: Flow<Units?> = store.data.map { prefs -> prefs[UnitsKey]?.let { name -> Units.entries.firstOrNull { it.name == name } } }

    suspend fun setUnits(units: Units?) {
        store.edit { if (units == null) it.remove(UnitsKey) else it[UnitsKey] = units.name }
    }

    /** The units to show: the user's pick, or the default for [countryCode], the phone's region. */
    fun effectiveUnits(countryCode: String): Flow<Units> = units.map { it ?: defaultUnits(countryCode) }

    /** Whether Pip reads the weather out on the Morning screen. */
    val readWeatherAloud: Flow<Boolean> = store.data.map { it[ReadWeatherAloud] ?: false }

    suspend fun setReadWeatherAloud(enabled: Boolean) {
        store.edit { it[ReadWeatherAloud] = enabled }
    }

    /** The text-to-speech voice, or null for the phone's default. */
    val voiceId: Flow<String?> = store.data.map { it[VoiceId] }

    suspend fun setVoiceId(id: String?) {
        store.edit { if (id == null) it.remove(VoiceId) else it[VoiceId] = id }
    }

    /** The last location fix, rounded, for when a new one can't be had. */
    val lastLocation: Flow<LastLocation?> = store.data.map { prefs ->
        val latitude = prefs[LastLatitude] ?: return@map null
        val longitude = prefs[LastLongitude] ?: return@map null
        val at = prefs[LastLocationAt] ?: return@map null
        LastLocation(Coordinates(latitude, longitude), Instant.fromEpochMilliseconds(at))
    }

    /** Keeps [coordinates] rounded, so a precise location is never stored. */
    suspend fun saveLastLocation(coordinates: Coordinates, at: Instant) {
        val rounded = coordinates.rounded()
        store.edit {
            it[LastLatitude] = rounded.latitude
            it[LastLongitude] = rounded.longitude
            it[LastLocationAt] = at.toEpochMilliseconds()
        }
    }

    suspend fun clearLastLocation() {
        store.edit {
            it.remove(LastLatitude)
            it.remove(LastLongitude)
            it.remove(LastLocationAt)
        }
    }

    /** After Open-Meteo answers 429, no weather requests until this time. */
    val weatherBackoffUntil: Flow<Instant?> = store.data.map { prefs -> prefs[WeatherBackoffUntil]?.let(Instant::fromEpochMilliseconds) }

    suspend fun setWeatherBackoffUntil(until: Instant?) {
        store.edit { if (until == null) it.remove(WeatherBackoffUntil) else it[WeatherBackoffUntil] = until.toEpochMilliseconds() }
    }

    private companion object {
        val FirstRunDone = booleanPreferencesKey("first_run_done")
        val WeatherEnabled = booleanPreferencesKey("weather_enabled")
        val WeatherSourceKey = stringPreferencesKey("weather_source")
        val UnitsKey = stringPreferencesKey("units")
        val ReadWeatherAloud = booleanPreferencesKey("read_weather_aloud")
        val VoiceId = stringPreferencesKey("voice_id")
        val LastLatitude = doublePreferencesKey("last_location_latitude")
        val LastLongitude = doublePreferencesKey("last_location_longitude")
        val LastLocationAt = longPreferencesKey("last_location_at")
        val WeatherBackoffUntil = longPreferencesKey("weather_backoff_until")
        val Intention = stringPreferencesKey("intention")
        val LastClosedAt = longPreferencesKey("last_closed_at")
        val DifficultyKey = stringPreferencesKey("difficulty")
        val SoundKey = stringPreferencesKey("sound")
        val RampKey = stringPreferencesKey("volume_ramp")
        val KettleCode = stringPreferencesKey("kettle_code")
    }
}

/** A rounded location fix and when it was taken. */
data class LastLocation(val coordinates: Coordinates, val at: Instant)

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
