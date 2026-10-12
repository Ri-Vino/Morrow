package app.morrow.timers

import app.morrow.platform.FakeSpeaker
import app.morrow.platform.SpeechPurpose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val noon = Instant.parse("2026-10-12T12:00:00Z")

@OptIn(ExperimentalCoroutinesApi::class)
class TimerVoiceTest {

    private val pasta = Timer(1, 10.minutes, noon, null, "Take the pasta off the hob", noon - 10.minutes)
    private val mum = Timer(2, 5.minutes, noon + 20.seconds, null, "Call Mum", noon - 5.minutes)
    private val plain = Timer(3, 25.minutes, noon, null, "", noon - 25.minutes)

    private val ringing = MutableStateFlow<List<Timer>>(emptyList())
    private val ducks = mutableListOf<Boolean>()

    private fun TestScope.follow(voice: TimerVoice): Job = backgroundScope.launch { voice.readNotes(ringing) { ducks += it } }

    private fun TestScope.advance(by: Duration) {
        advanceTimeBy(by)
        runCurrent()
    }

    private val FakeSpeaker.said get() = spoken.map { it.first }

    @Test
    fun readsTheNoteAfterTwoSecondsThenEveryThirtySecondsThreeTimes() = runTest {
        val speaker = FakeSpeaker()
        ringing.value = listOf(pasta)
        follow(TimerVoice(speaker))

        advance(2.seconds - 1.milliseconds)
        assertEquals(emptyList(), speaker.said)

        advance(1.milliseconds)
        assertEquals(listOf("Take the pasta off the hob" to SpeechPurpose.TIMER_NOTE), speaker.spoken)

        advance(30.seconds - 1.milliseconds)
        assertEquals(1, speaker.spoken.size)
        advance(1.milliseconds)
        assertEquals(2, speaker.spoken.size)

        advance(30.seconds)
        assertEquals(3, speaker.spoken.size)

        advance(10.minutes)
        assertEquals(3, speaker.spoken.size)
    }

    @Test
    fun theNextReadingComesThirtySecondsAfterTheLastOneEnds() = runTest {
        val speaker = FakeSpeaker(talkFor = 4.seconds)
        ringing.value = listOf(pasta)
        follow(TimerVoice(speaker))

        advance(2.seconds + 4.seconds + 30.seconds - 1.milliseconds)
        assertEquals(1, speaker.spoken.size)
        advance(1.milliseconds)
        assertEquals(2, speaker.spoken.size)
    }

    @Test
    fun timersWithoutANoteAreNotRead() = runTest {
        val speaker = FakeSpeaker()
        val voice = TimerVoice(speaker)
        ringing.value = listOf(plain)
        follow(voice)

        advance(10.minutes)

        assertEquals(emptyList(), speaker.said)
        assertEquals(emptyList(), ducks)
        assertFalse(voice.speaking.value)
    }

    @Test
    fun theChimeIsDuckedOnlyWhilePipSpeaks() = runTest {
        val voice = TimerVoice(FakeSpeaker(talkFor = 3.seconds))
        ringing.value = listOf(pasta)
        follow(voice)

        advance(2.seconds)
        assertTrue(voice.speaking.value)
        assertEquals(listOf(true), ducks)

        advance(3.seconds)
        assertFalse(voice.speaking.value)
        assertEquals(listOf(true, false), ducks)

        advance(10.minutes)
        assertEquals(listOf(true, false, true, false, true, false), ducks)
    }

    @Test
    fun severalNotesAreReadTogetherInTheOrderTheTimersEnded() = runTest {
        val speaker = FakeSpeaker()
        ringing.value = listOf(pasta, plain, mum)
        follow(TimerVoice(speaker))

        advance(2.seconds)

        assertEquals(listOf("Take the pasta off the hob. Call Mum."), speaker.said)
    }

    @Test
    fun stoppingTheTimersCutsPipOffAtOnce() = runTest {
        val speaker = FakeSpeaker(talkFor = 5.seconds)
        val voice = TimerVoice(speaker)
        ringing.value = listOf(pasta)
        follow(voice)

        advance(3.seconds)
        ringing.value = emptyList()
        runCurrent()

        assertFalse(voice.speaking.value)
        assertEquals(listOf(true, false), ducks)
        assertEquals(1, speaker.stops)
        advance(10.minutes)
        assertEquals(1, speaker.spoken.size)
    }

    @Test
    fun cancellingTheReadingCutsPipOffAndBringsTheChimeBack() = runTest {
        val speaker = FakeSpeaker(talkFor = 5.seconds)
        val voice = TimerVoice(speaker)
        ringing.value = listOf(pasta)
        val reading = follow(voice)

        advance(3.seconds)
        reading.cancel()
        runCurrent()

        assertFalse(voice.speaking.value)
        assertEquals(listOf(true, false), ducks)
        assertEquals(1, speaker.stops)
    }

    @Test
    fun hushCutsOffTheReadingButNotTheSchedule() = runTest {
        val speaker = FakeSpeaker(talkFor = 5.seconds)
        val voice = TimerVoice(speaker)
        ringing.value = listOf(pasta)
        follow(voice)

        voice.hush()
        assertEquals(0, speaker.stops)

        advance(3.seconds)
        voice.hush()
        runCurrent()

        assertEquals(1, speaker.stops)
        assertFalse(voice.speaking.value)
        assertEquals(listOf(true, false), ducks)
        advance(30.seconds)
        assertEquals(2, speaker.spoken.size)
    }

    @Test
    fun aTimerEndingMeanwhileStartsTheReadingsOverWithBothNotes() = runTest {
        val speaker = FakeSpeaker()
        ringing.value = listOf(pasta)
        follow(TimerVoice(speaker))

        advance(2.seconds)
        advance(18.seconds)
        ringing.value = listOf(pasta, mum)
        advance(2.seconds)
        advance(10.minutes)

        val both = "Take the pasta off the hob. Call Mum."
        assertEquals(listOf("Take the pasta off the hob", both, both, both), speaker.said)
    }

    @Test
    fun aTimerEndingWhilePipSpeaksCutsHimOffAndStartsOver() = runTest {
        val speaker = FakeSpeaker(talkFor = 5.seconds)
        ringing.value = listOf(pasta)
        follow(TimerVoice(speaker))

        advance(3.seconds)
        ringing.value = listOf(pasta, mum)
        runCurrent()
        assertEquals(1, speaker.stops)

        advance(2.seconds)
        assertEquals(listOf("Take the pasta off the hob", "Take the pasta off the hob. Call Mum."), speaker.said)
    }

    @Test
    fun aTimerWithoutANoteEndingMeanwhileChangesNothing() = runTest {
        val speaker = FakeSpeaker()
        ringing.value = listOf(pasta)
        follow(TimerVoice(speaker))

        advance(10.seconds)
        ringing.value = listOf(pasta, plain)
        advance(10.minutes)

        assertEquals(3, speaker.spoken.size)
    }

    @Test
    fun aTimerStoppedOnItsOwnIsNotReadAgain() = runTest {
        val speaker = FakeSpeaker()
        ringing.value = listOf(pasta, mum)
        follow(TimerVoice(speaker))

        advance(2.seconds)
        advance(8.seconds)
        ringing.value = listOf(mum)
        advance(10.minutes)

        assertEquals(listOf("Take the pasta off the hob. Call Mum.", "Call Mum", "Call Mum", "Call Mum"), speaker.said)
    }

    @Test
    fun noteEndingsAreKeptAndOthersGetAFullStop() {
        assertEquals("Eggs!", spokenNotes(listOf("Eggs!")))
        assertEquals("Eggs! Tea? Done. Call Mum.", spokenNotes(listOf("Eggs!", "Tea?", "Done.", "Call Mum")))
    }
}
