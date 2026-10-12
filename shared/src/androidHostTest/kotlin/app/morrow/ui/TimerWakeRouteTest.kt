package app.morrow.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.morrow.alarms.FakeClock
import app.morrow.data.FakeTimerDao
import app.morrow.platform.FakeSpeaker
import app.morrow.platform.FakeTimerNotifier
import app.morrow.platform.FakeTimerScheduler
import app.morrow.platform.SpeechPurpose
import app.morrow.timers.TimerCoordinator
import app.morrow.timers.TimerRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** iOS opening Morrow from a timer's alert, which the system has already rung. */
@RunWith(RobolectricTestRunner::class)
class TimerWakeRouteTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val noon = Instant.parse("2026-10-12T12:00:00Z")
    private val clock = FakeClock(noon)
    private val repository = TimerRepository(FakeTimerDao())
    private val scheduler = FakeTimerScheduler()
    private val timers = TimerCoordinator(repository, scheduler, FakeTimerNotifier(), clock)
    private var speaker = FakeSpeaker()
    private var done = 0

    private fun open(vararg ids: Long) {
        compose.setContent { TimerWakeRoute(timers, speaker, ids.toList(), onDone = { done++ }, zone = TimeZone.UTC) }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun showsTheTimerAndReadsItsNoteOnce() {
        val id = runBlocking { timers.start(10.minutes, "Take the pasta off the hob")!! }
        clock.now = noon + 11.minutes

        open(id)

        waitForText("Take the pasta off the hob")
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("Take the pasta off the hob" to SpeechPurpose.TIMER_NOTE), speaker.spoken)
        assertEquals(0, done)
    }

    @Test
    fun pipIsShownTalkingWhileHeReadsAndNothingPromisesTheScreenStopsByItself() {
        speaker = FakeSpeaker(talkFor = 10.minutes)
        val id = runBlocking { timers.start(10.minutes, "Take the pasta off the hob")!! }
        clock.now = noon + 11.minutes

        open(id)

        waitForText("Pip is reading your note")
        assertEquals(0, compose.onAllNodesWithText("Stops by itself", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun stopRemovesTheTimerAndCloses() {
        val id = runBlocking { timers.start(10.minutes)!! }
        clock.now = noon + 10.minutes
        open(id)
        waitForText("Your 10 min timer is done")

        compose.onNodeWithContentDescription("Stop the timer").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { done == 1 }
        assertNull(runBlocking { repository.get(id) })
        // Nothing to read without a note.
        assertEquals(emptyList(), speaker.spoken)
    }

    @Test
    fun aMinuteMoreRingsItAgainInAMinute() {
        val id = runBlocking { timers.start(10.minutes, "Eggs")!! }
        clock.now = noon + 10.minutes
        open(id)
        waitForText("Eggs")

        compose.onNodeWithContentDescription("Ring again in a minute").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { done == 1 }
        assertEquals(mapOf(id to noon + 11.minutes), scheduler.scheduled)
    }

    @Test
    fun aTimerStoppedSinceShowsNothing() {
        val id = runBlocking { timers.start(10.minutes)!! }
        clock.now = noon + 11.minutes
        runBlocking { timers.stop(id) }

        open(id)

        compose.waitUntil(timeoutMillis = 5_000) { done == 1 }
    }

    @Test
    fun aPausedTimerResumedAndRungByTheSystemStillShowsWhenOpened() {
        val id = runBlocking { timers.start(10.minutes, "Take the pasta off the hob")!!.also { timers.pause(it) } }
        clock.now = noon + 30.minutes

        open(id)

        waitForText("Take the pasta off the hob")
        assertEquals(0, done)
    }
}
