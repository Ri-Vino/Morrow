package app.morrow.ui.morning

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class MorningModelTest {

    private val zone = TimeZone.of("Europe/London")
    private val woke = LocalDateTime(2026, 10, 8, 6, 42).toInstant(zone)

    @Test
    fun summarisesABelievableNight() {
        val model = morningModel(woke, wentToBed = woke - 7.hours - 10.minutes, zone)
        assertEquals("Good morning.", model.hello)
        assertEquals("You were up at 06:42, after about 7h 10m of sleep.", model.summary)
    }

    @Test
    fun leavesOutSleepWhenTheBedtimeGuessIsUnlikely() {
        val nap = morningModel(woke, wentToBed = woke - 30.minutes, zone)
        val stale = morningModel(woke, wentToBed = woke - 3.days, zone)
        val unknown = morningModel(woke, wentToBed = null, zone)
        for (model in listOf(nap, stale, unknown)) {
            assertEquals("You were up at 06:42. Nicely done.", model.summary)
        }
    }

    @Test
    fun greetsByTheTimeOfWaking() {
        val afternoon = LocalDateTime(2026, 10, 8, 14, 0).toInstant(zone)
        assertEquals("Good afternoon.", morningModel(afternoon, null, zone).hello)
    }
}
