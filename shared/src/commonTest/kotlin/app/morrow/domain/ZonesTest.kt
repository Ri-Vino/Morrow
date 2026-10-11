package app.morrow.domain

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ZonesTest {

    @Test
    fun bothTheOldAndNewNameOfARenamedZoneResolve() {
        assertNotNull(timeZoneOrNull("Europe/Kyiv"))
        assertNotNull(timeZoneOrNull("Europe/Kiev"))
        assertNotNull(timeZoneOrNull("Asia/Kolkata"))
        assertNotNull(timeZoneOrNull("Asia/Calcutta"))
    }

    @Test
    fun aZoneTooNewForThePhoneFallsBackToOneOnTheSameClock() {
        assertNotNull(timeZoneOrNull("America/Ciudad_Juarez"))
    }

    @Test
    fun anUnknownZoneIsNull() {
        assertNull(timeZoneOrNull("Atlantis/Lost_City"))
    }

    @Test
    fun aZonedAlarmUsesTheRenamedZone() {
        val alarm = Alarm(1, 9, 0, "Call", emptySet(), DefaultGames, enabled = true, timeZone = "Europe/Kiev")
        assertNotNull(alarm.ownZone())
    }
}
