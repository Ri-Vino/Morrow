package app.morrow.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every bundled sound has a file on both platforms, made by tools/make_sounds.py, and the picker has no sound twice. */
class WakeSoundFilesTest {

    // Gradle runs a module's tests from the module's own folder.
    private val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/make_sounds.py").exists() }

    @Test
    fun everySoundHasAnOggForAndroidAndACafForIos() {
        val missing = WakeSound.entries.flatMap { sound ->
            val name = sound.name.lowercase()
            listOf("androidApp/src/main/res/raw/$name.ogg", "iosApp/iosApp/Sounds/$name.caf").filterNot { File(repo, it).isFile }
        }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everySoundIsMadeByAScript() {
        val generated = File(repo, "tools/make_sounds.py").readText()
        val recorded = File(repo, "tools/clip_recordings.py").readText()
        val unmade = WakeSound.entries.map { it.name.lowercase() }.filterNot { "\"$it\"" in generated || "\"$it\"" in recorded }
        assertEquals(emptyList(), unmade)
    }

    @Test
    fun everyRecordingIsCredited() {
        val credits = File(repo, "shared/src/commonMain/composeResources/files/licenses/recordings.md").readText()
        val uncredited = WakeSound.entries.filter { it.category == SoundCategory.CLASSICAL }.filterNot { "| ${it.displayName} |" in credits }
        assertEquals(emptyList(), uncredited)
    }

    @Test
    fun soundsHaveDistinctNames() {
        assertEquals(WakeSound.entries.size, WakeSound.entries.map { it.displayName }.distinct().size)
        assertTrue(WakeSound.entries.all { it.displayName.first().isUpperCase() })
    }
}
