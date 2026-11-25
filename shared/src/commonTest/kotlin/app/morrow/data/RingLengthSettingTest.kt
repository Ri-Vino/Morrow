package app.morrow.data

import app.morrow.domain.RingLength
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RingLengthSettingTest {
    private val settings = MorrowSettings(InMemoryPreferences())

    @Test
    fun anAlarmRingsForTenMinutesUntilAnotherLengthIsPicked() = runTest {
        assertEquals(RingLength.TEN_MINUTES, settings.alarmSetup.first().ringFor)

        settings.setRingLength(RingLength.NO_LIMIT)

        assertEquals(RingLength.NO_LIMIT, settings.alarmSetup.first().ringFor)
    }

    @Test
    fun theLengthsOnOffer() {
        assertEquals(listOf(10.minutes, 30.minutes, 1.hours, null), RingLength.entries.map { it.limit })
        assertNull(RingLength.NO_LIMIT.inWords)
    }
}
