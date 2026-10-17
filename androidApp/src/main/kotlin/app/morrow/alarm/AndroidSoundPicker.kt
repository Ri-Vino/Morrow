package app.morrow.alarm

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.morrow.platform.PickedSound
import app.morrow.platform.SoundPicker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Picks a sound with the system's file picker and copies it into device-protected storage, so it can ring after a
 * reboot before the phone's first unlock, and still rings if the original is moved or deleted. Create it before the
 * activity starts.
 */
class AndroidSoundPicker(private val activity: ComponentActivity) : SoundPicker {

    private var waiting: CompletableDeferred<Uri?>? = null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        waiting?.complete(uri)
        waiting = null
    }

    override suspend fun pick(): PickedSound {
        val result = CompletableDeferred<Uri?>()
        waiting?.cancel()
        waiting = result
        picker.launch(arrayOf("audio/*"))
        val uri = result.await() ?: return PickedSound.Cancelled
        return withContext(Dispatchers.IO) { copy(uri) }
    }

    override fun delete(path: String) {
        // Only ever a copy this picker made.
        val file = File(path)
        if (file.parentFile == folder()) file.delete()
    }

    private fun copy(uri: Uri): PickedSound {
        val resolver = activity.contentResolver
        var name = "Sound"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        if (size > MAX_BYTES) return PickedSound.Failed("That file is too big. Pick one under 20 MB.")
        val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.lowercase()
        val file = File(folder(), UUID.randomUUID().toString() + (extension?.let { ".$it" } ?: ""))
        try {
            val input = resolver.openInputStream(uri) ?: return PickedSound.Failed("That file couldn’t be opened.")
            input.use { from -> file.outputStream().use { to -> if (from.copyTo(to) > MAX_BYTES) error("too big") } }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't copy $uri", e)
            file.delete()
            return PickedSound.Failed("That file couldn’t be copied. Try another.")
        }
        if (!plays(file)) {
            file.delete()
            return PickedSound.Failed("That file doesn’t seem to be a sound this phone can play.")
        }
        return PickedSound.Copied(name.substringBeforeLast('.').trim().take(MAX_NAME).ifEmpty { "Sound" }, file.absolutePath)
    }

    /** Whether the file has a sound of some length in it. */
    private fun plays(file: File): Boolean = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.absolutePath)
                val audio = extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                val length = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                audio && length > 0
            } finally {
                release()
            }
        }
    }.getOrDefault(false)

    private fun folder(): File = File(activity.createDeviceProtectedStorageContext().filesDir, "sounds").apply { mkdirs() }

    private companion object {
        const val TAG = "SoundPicker"
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MAX_NAME = 40
    }
}
