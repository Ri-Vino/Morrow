package app.morrow.platform

/** Brings a sound in from the phone for an alarm to ring with. */
interface SoundPicker {
    /** Asks the user to pick an audio file, and copies it in if it plays. */
    suspend fun pick(): PickedSound

    /** Deletes a copy [pick] made, once its sound is off the list. */
    fun delete(path: String)
}

sealed interface PickedSound {
    /** Copied to [path]; [name] is the file's, without its extension. */
    data class Copied(val name: String, val path: String) : PickedSound

    data object Cancelled : PickedSound

    /** Picked, but it couldn't be read, played, or was too big; [reason] says which, for the user. */
    data class Failed(val reason: String) : PickedSound
}
