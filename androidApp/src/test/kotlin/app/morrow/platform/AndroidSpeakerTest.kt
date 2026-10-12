package app.morrow.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSpeakerTest {

    private class FakeEngine(var voices: List<EngineVoice> = emptyList(), var accepts: Boolean = true) : SpeechEngine {
        val spoken = mutableListOf<Pair<String, String>>()
        val voicesUsed = mutableListOf<String>()
        var stops = 0
        var shutDown = false
        var default: EngineVoice? = offline("en-gb-x-default")

        override fun voices() = voices

        override fun defaultVoice() = default

        override fun useVoice(name: String) {
            voicesUsed += name
        }

        override fun speak(text: String, id: String): Boolean {
            spoken += id to text
            return accepts
        }

        override fun stop() {
            stops++
        }

        override fun shutdown() {
            shutDown = true
        }

        val lastId get() = spoken.last().first
    }

    /** Starts engines at once, or when the test says so with [finishStarting]. */
    private class FakeFactory(var installed: Boolean = true, val startsAtOnce: Boolean = true) : EngineFactory {
        val engines = mutableListOf<FakeEngine>()
        var starts = 0

        /** Changes the next engine started, once. */
        var prepare: ((FakeEngine) -> Unit)? = null
        private var onBegin: (String) -> Unit = {}
        private var onEnd: (String) -> Unit = {}
        private var onStarted: ((SpeechEngine?) -> Unit)? = null

        override fun start(onUtteranceStart: (id: String) -> Unit, onUtteranceEnd: (id: String) -> Unit, onStarted: (SpeechEngine?) -> Unit) {
            starts++
            onBegin = onUtteranceStart
            onEnd = onUtteranceEnd
            this.onStarted = onStarted
            if (startsAtOnce) finishStarting()
        }

        fun finishStarting() {
            val engine = if (installed) {
                FakeEngine().also {
                    prepare?.invoke(it)
                    prepare = null
                    engines += it
                }
            } else {
                null
            }
            onStarted?.invoke(engine)
        }

        fun utteranceBegan(id: String) = onBegin(id)

        fun utteranceEnded(id: String) = onEnd(id)

        val engine get() = engines.last()
    }

    private fun TestScope.speaker(factory: FakeFactory, phone: Locale = Locale.forLanguageTag("en-GB")) =
        AndroidSpeaker(factory, StandardTestDispatcher(testScheduler)) { phone }

    @Test
    fun theEngineStartsWhenFirstNeededAndNotBefore() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        speaker.setVoice("whatever")
        runCurrent()
        assertEquals(0, factory.starts)

        val job = launch { speaker.speak("Hello", SpeechPurpose.SAMPLE) }
        runCurrent()

        assertEquals(1, factory.starts)
        assertEquals(listOf("Hello"), factory.engine.spoken.map { it.second })
        factory.utteranceEnded(factory.engine.lastId)
        job.join()
    }

    @Test
    fun speakReturnsOnlyOnceTheUtteranceIsDone() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var returned = false

        val job = launch {
            speaker.speak("Hello", SpeechPurpose.SAMPLE)
            returned = true
        }
        runCurrent()
        assertFalse(returned)

        factory.utteranceEnded("someone else's")
        runCurrent()
        assertFalse(returned)

        factory.utteranceEnded(factory.engine.lastId)
        runCurrent()
        assertTrue(returned)
        job.join()
    }

    @Test
    fun cancellingTheCallStopsTheSpeech() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        val job = launch { speaker.speak("Hello", SpeechPurpose.SAMPLE) }
        runCurrent()

        job.cancel()
        runCurrent()

        assertEquals(1, factory.engine.stops)
    }

    @Test
    fun stopEndsTheSpeechAndTheEngine() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var returned = false
        launch {
            speaker.speak("Hello", SpeechPurpose.TIMER_NOTE)
            returned = true
        }
        runCurrent()

        speaker.stop(SpeechPurpose.TIMER_NOTE)
        runCurrent()

        assertTrue(returned)
        assertEquals(1, factory.engine.stops)
    }

    @Test
    fun aNewUtteranceCutsOffTheOldOne() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var firstReturned = false
        var secondReturned = false
        launch {
            speaker.speak("First", SpeechPurpose.TIMER_NOTE)
            firstReturned = true
        }
        runCurrent()
        val firstId = factory.engine.lastId

        launch {
            speaker.speak("Second", SpeechPurpose.TIMER_NOTE)
            secondReturned = true
        }
        runCurrent()

        assertTrue(firstReturned)
        assertFalse(secondReturned)
        // The engine reports the first one stopped; that mustn't end the second.
        factory.utteranceEnded(firstId)
        runCurrent()
        assertFalse(secondReturned)
        assertEquals(0, factory.engine.stops)

        factory.utteranceEnded(factory.engine.lastId)
        runCurrent()
        assertTrue(secondReturned)
    }

    @Test
    fun theEngineIsReleasedThirtySecondsAfterTheLastWords() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        val job = launch { speaker.speak("Hello", SpeechPurpose.SAMPLE) }
        runCurrent()
        factory.utteranceEnded(factory.engine.lastId)
        job.join()

        advanceTimeBy(29.seconds)
        runCurrent()
        assertFalse(factory.engine.shutDown)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertTrue(factory.engine.shutDown)
    }

    @Test
    fun speakingAgainRestartsAReleasedEngineAndKeepsALiveOneAlive() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)

        suspend fun sayOnce() {
            val job = launch { speaker.speak("Hello", SpeechPurpose.SAMPLE) }
            runCurrent()
            factory.utteranceEnded(factory.engine.lastId)
            job.join()
        }
        sayOnce()
        advanceTimeBy(20.seconds)
        sayOnce()
        advanceTimeBy(20.seconds)
        runCurrent()
        assertEquals(1, factory.starts)
        assertFalse(factory.engine.shutDown)

        advanceTimeBy(10.seconds)
        runCurrent()
        assertTrue(factory.engine.shutDown)

        sayOnce()
        assertEquals(2, factory.starts)
        assertEquals(2, factory.engines.size)
    }

    @Test
    fun theEngineIsNotReleasedWhileItIsStillSpeaking() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        val job = launch { speaker.speak("A long note", SpeechPurpose.TIMER_NOTE) }
        runCurrent()

        advanceTimeBy(45.seconds)
        runCurrent()

        assertFalse(factory.engine.shutDown)
        factory.utteranceEnded(factory.engine.lastId)
        job.join()
    }

    @Test
    fun withNoEngineNothingIsSaidAndCallsReturnAtOnce() = runTest {
        val factory = FakeFactory(installed = false)
        val speaker = speaker(factory)

        speaker.speak("Hello", SpeechPurpose.SAMPLE)

        assertEquals(emptyList(), speaker.voices())
        assertEquals(0, factory.engines.size)
    }

    @Test
    fun anEngineThatTakesTooLongToStartIsGivenUpOnAndReleasedWhenItArrives() = runTest {
        val factory = FakeFactory(startsAtOnce = false)
        val speaker = speaker(factory)
        var returned = false
        launch {
            speaker.speak("Hello", SpeechPurpose.SAMPLE)
            returned = true
        }
        runCurrent()
        advanceTimeBy(4.seconds)
        runCurrent()
        assertFalse(returned)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertTrue(returned)

        factory.finishStarting()
        runCurrent()
        assertEquals(emptyList(), factory.engine.spoken)
        assertFalse(factory.engine.shutDown)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertTrue(factory.engine.shutDown)
    }

    @Test
    fun twoCallsWhileTheEngineStartsShareOneStart() = runTest {
        val factory = FakeFactory(startsAtOnce = false)
        val speaker = speaker(factory)
        val speech = launch { speaker.speak("First", SpeechPurpose.SAMPLE) }
        launch { speaker.voices() }
        runCurrent()

        factory.finishStarting()
        runCurrent()

        assertEquals(1, factory.starts)
        factory.utteranceEnded(factory.engine.lastId)
        speech.join()
    }

    @Test
    fun aStopWhileTheEngineIsStartingMeansTheWordsAreNeverSaid() = runTest {
        val factory = FakeFactory(startsAtOnce = false)
        val speaker = speaker(factory)
        var returned = false
        launch {
            speaker.speak("Hello", SpeechPurpose.TIMER_NOTE)
            returned = true
        }
        runCurrent()

        speaker.stop(SpeechPurpose.TIMER_NOTE)
        factory.finishStarting()
        runCurrent()

        assertTrue(returned)
        assertEquals(emptyList(), factory.engine.spoken)
    }

    @Test
    fun anEngineThatRefusesTheWordsIsDroppedAndStartedAgainNextTime() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        factory.prepare = { it.accepts = false }

        speaker.speak("Hello", SpeechPurpose.SAMPLE)

        assertTrue(factory.engine.shutDown)
        val second = launch { speaker.speak("Again", SpeechPurpose.SAMPLE) }
        runCurrent()
        assertEquals(2, factory.starts)
        assertEquals(listOf("Again"), factory.engine.spoken.map { it.second })
        factory.utteranceEnded(factory.engine.lastId)
        second.join()
    }

    @Test
    fun anUtteranceThatNeverEndsIsGivenUpOnAfterAMinute() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var returned = false
        launch {
            speaker.speak("Hello", SpeechPurpose.TIMER_NOTE)
            returned = true
        }
        runCurrent()

        advanceTimeBy(59.seconds)
        runCurrent()
        assertFalse(returned)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertTrue(returned)
        assertEquals(1, factory.engine.stops)
    }

    @Test
    fun blankWordsAreNotSpokenAndDoNotStartTheEngine() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)

        speaker.speak("  ", SpeechPurpose.TIMER_NOTE)

        assertEquals(0, factory.starts)
    }

    @Test
    fun theChosenVoiceIsUsedFromTheStartAndWhenItChanges() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        factory.prepare = { it.voices = listOf(offline("en-gb-x-rjs-local")) }
        speaker.setVoice("en-gb-x-rjs-local")
        sayOnce(speaker, factory)
        assertEquals(listOf("en-gb-x-rjs-local"), factory.engine.voicesUsed)

        speaker.setVoice(null)
        sayOnce(speaker, factory)

        assertEquals(listOf("en-gb-x-rjs-local", "en-gb-x-default"), factory.engine.voicesUsed)
    }

    @Test
    fun aDefaultVoiceThatNeedsTheNetworkIsPassedOverForAnInstalledOne() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        factory.prepare = {
            val cloud = EngineVoice("en-gb-x-cloud", Locale.forLanguageTag("en-GB"), 500, needsNetwork = true, installed = true)
            it.default = cloud
            it.voices = listOf(cloud, offline("en-gb-x-rjs-local"))
        }

        sayOnce(speaker, factory)

        assertEquals(listOf("en-gb-x-rjs-local"), factory.engine.voicesUsed)
    }

    @Test
    fun aDefaultVoiceThatIsNotInstalledIsPassedOverForAnInstalledOne() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        factory.prepare = {
            val big = EngineVoice("en-gb-x-big", Locale.forLanguageTag("en-GB"), 500, needsNetwork = false, installed = false)
            it.default = big
            it.voices = listOf(big, offline("en-gb-x-rjs-local"))
        }

        sayOnce(speaker, factory)

        assertEquals(listOf("en-gb-x-rjs-local"), factory.engine.voicesUsed)
    }

    @Test
    fun withNoVoiceThatWorksOfflineNothingIsSaid() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        factory.prepare = {
            val cloud = EngineVoice("en-gb-x-cloud", Locale.forLanguageTag("en-GB"), 500, needsNetwork = true, installed = true)
            it.default = cloud
            it.voices = listOf(cloud)
        }

        speaker.speak("Hello", SpeechPurpose.TIMER_NOTE)

        assertEquals(emptyList(), factory.engine.spoken)
    }

    @Test
    fun stoppingOtherSpeechLeavesATimersNoteAlone() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var returned = false
        launch {
            speaker.speak("Take the pasta off the hob", SpeechPurpose.TIMER_NOTE)
            returned = true
        }
        runCurrent()

        speaker.stop(SpeechPurpose.MORNING_WEATHER)
        runCurrent()

        assertFalse(returned)
        assertEquals(0, factory.engine.stops)
        factory.utteranceEnded(factory.engine.lastId)
        runCurrent()
        assertTrue(returned)
    }

    @Test
    fun otherSpeechIsSkippedWhileATimersNoteIsRead() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var noteReturned = false
        launch {
            speaker.speak("Take the pasta off the hob", SpeechPurpose.TIMER_NOTE)
            noteReturned = true
        }
        runCurrent()

        speaker.speak("Good morning", SpeechPurpose.MORNING_WEATHER)

        assertFalse(noteReturned)
        assertEquals(listOf("Take the pasta off the hob"), factory.engine.spoken.map { it.second })
        factory.utteranceEnded(factory.engine.lastId)
        runCurrent()
        assertTrue(noteReturned)
    }

    @Test
    fun aTimersNoteCutsOffOtherSpeech() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var morningReturned = false
        launch {
            speaker.speak("Good morning", SpeechPurpose.MORNING_WEATHER)
            morningReturned = true
        }
        runCurrent()

        val note = launch { speaker.speak("Take the pasta off the hob", SpeechPurpose.TIMER_NOTE) }
        runCurrent()

        assertTrue(morningReturned)
        factory.utteranceEnded(factory.engine.lastId)
        note.join()
    }

    @Test
    fun onStartRunsOnceWhenTheWordsBeginAndNotForAnotherUtterance() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        var starts = 0
        val job = launch { speaker.speak("Hello", SpeechPurpose.TIMER_NOTE) { starts++ } }
        runCurrent()
        assertEquals(0, starts)

        factory.utteranceBegan("someone else's")
        factory.utteranceBegan(factory.engine.lastId)
        factory.utteranceBegan(factory.engine.lastId)
        runCurrent()
        assertEquals(1, starts)

        factory.utteranceEnded(factory.engine.lastId)
        job.join()
    }

    @Test
    fun onStartNeverRunsWhenThereIsNoEngine() = runTest {
        val factory = FakeFactory(installed = false)
        val speaker = speaker(factory)
        var started = false

        speaker.speak("Hello", SpeechPurpose.TIMER_NOTE) { started = true }

        assertFalse(started)
    }

    private suspend fun TestScope.sayOnce(speaker: AndroidSpeaker, factory: FakeFactory) {
        val job = launch { speaker.speak("Hello", SpeechPurpose.SAMPLE) }
        runCurrent()
        factory.utteranceEnded(factory.engine.lastId)
        job.join()
    }

    @Test
    fun voicesAreThoseForThePhonesLanguageThatWorkOffline() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)
        val british = EngineVoice("gb", Locale.forLanguageTag("en-GB"), 400, needsNetwork = false, installed = true)
        val online = EngineVoice("online", Locale.forLanguageTag("en-GB"), 500, needsNetwork = true, installed = true)
        val french = EngineVoice("fr", Locale.forLanguageTag("fr-FR"), 400, needsNetwork = false, installed = true)
        factory.prepare = { it.voices = listOf(online, french, british) }

        val voices = speaker.voices()

        assertEquals(listOf(VoiceInfo("gb", "English (UK)", "English (UK)")), voices)
    }

    @Test
    fun listingVoicesStartsTheEngineAndLetsItGoAgainAfterThirtySeconds() = runTest {
        val factory = FakeFactory()
        val speaker = speaker(factory)

        speaker.voices()
        advanceTimeBy(29.seconds)
        runCurrent()
        assertFalse(factory.engine.shutDown)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertTrue(factory.engine.shutDown)
    }
}

private fun offline(name: String) = EngineVoice(name, Locale.forLanguageTag("en-GB"), 300, needsNetwork = false, installed = true)
