package app.morrow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Settings without a file: DataStore's file store can't replace its file on a Windows host. */
class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()

    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = lock.withLock {
        transform(state.value).also { state.value = it }
    }
}
