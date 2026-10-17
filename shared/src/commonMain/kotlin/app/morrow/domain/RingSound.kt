package app.morrow.domain

/** What an alarm rings with: one of the sounds bundled with the app, or one of the user's own. */
sealed interface RingSound {
    data class Bundled(val sound: WakeSound) : RingSound

    /** The [OwnSound] with this [id]. */
    data class Own(val id: Long) : RingSound
}

/** A sound the user added from their phone, copied in to [path] so it rings even if the original goes. */
data class OwnSound(val id: Long, val name: String, val path: String)

/** As stored: a bundled sound's name, or "own:" and the own sound's id. */
fun RingSound.encode(): String = when (this) {
    is RingSound.Bundled -> sound.name
    is RingSound.Own -> "$OWN_PREFIX$id"
}

/** Reads [encode]'s text; null for nothing, or for a sound this version doesn't know, which rings with the default. */
fun decodeRingSound(text: String?): RingSound? {
    if (text == null) return null
    if (text.startsWith(OWN_PREFIX)) return text.removePrefix(OWN_PREFIX).toLongOrNull()?.let(RingSound::Own)
    return WakeSound.entries.firstOrNull { it.name == text }?.let(RingSound::Bundled)
}

private const val OWN_PREFIX = "own:"
