package app.morrow.data

import app.morrow.domain.Alarm
import app.morrow.domain.DefaultGames
import app.morrow.domain.OwnSound
import app.morrow.domain.RingSound
import app.morrow.domain.WakeSound
import app.morrow.domain.decodeRingSound
import app.morrow.domain.encode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OwnSoundsTest {

    private val alarmDao = FakeAlarmDao()
    private val alarms = AlarmRepository(alarmDao)
    private val sounds = OwnSounds(FakeOwnSoundDao(), alarmDao)

    private fun alarm(sound: RingSound?) = Alarm(0, 7, 0, "Alarm", emptySet(), DefaultGames, enabled = true, sound = sound)

    @Test
    fun soundsAreListedInTheOrderTheyWereAdded() = runTest {
        val song = sounds.add("Our song", "/sounds/a.mp3", addedAt = 2)
        val bell = sounds.add("Bell", "/sounds/b.ogg", addedAt = 5)
        assertEquals(listOf(song, bell), sounds.sounds.first())
        assertEquals(OwnSound(song.id, "Our song", "/sounds/a.mp3"), sounds.get(song.id))
    }

    @Test
    fun removingASoundPutsItsAlarmsBackOnTheDefault() = runTest {
        val song = sounds.add("Our song", "/sounds/a.mp3", addedAt = 1)
        val mine = alarms.save(alarm(RingSound.Own(song.id)))
        val chimes = alarms.save(alarm(RingSound.Bundled(WakeSound.WIND_CHIMES)))

        assertEquals(song, sounds.remove(song.id))

        assertEquals(emptyList(), sounds.sounds.first())
        assertNull(alarms.get(mine)?.sound)
        assertEquals(RingSound.Bundled(WakeSound.WIND_CHIMES), alarms.get(chimes)?.sound)
        assertNull(sounds.remove(song.id))
    }

    @Test
    fun soundsAreStoredByNameOrOwnId() {
        assertEquals("MARIMBA", RingSound.Bundled(WakeSound.MARIMBA).encode())
        assertEquals("own:12", RingSound.Own(12).encode())
        assertEquals(RingSound.Own(12), decodeRingSound("own:12"))
        assertEquals(RingSound.Bundled(WakeSound.BIRDSONG), decodeRingSound("BIRDSONG"))
        // One this version doesn't know rings with the default.
        assertNull(decodeRingSound("KAZOO"))
        assertNull(decodeRingSound("own:x"))
        assertNull(decodeRingSound(null))
    }
}
