package app.morrow.ui.firstrun

import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.IosPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupModelTest {

    @Test
    fun androidAsksForFivePermissionsWithLocationLastAndOptional() {
        assertEquals(5, AndroidPermissions.size)
        assertEquals(AlarmPermission.LOCATION, AndroidPermissions.last())
        assertFalse(AlarmPermission.LOCATION.required)
        assertEquals("Location", AlarmPermission.LOCATION.title)
        assertEquals("Optional. For your local weather; a city works too.", AlarmPermission.LOCATION.description)
    }

    @Test
    fun nothingAllowedYet() {
        val model = setupModel(AndroidPermissions, AndroidPermissions)
        assertEquals("0 of 5 ready", model.readyText)
        assertEquals(0f, model.progress)
        assertFalse(model.ready)
        assertEquals("Alarms can’t ring until the first three are allowed.", model.note)
    }

    @Test
    fun requiredOnesAllowedIsReadyWithoutTheOptionalOnes() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.BATTERY, AlarmPermission.LOCATION))
        assertEquals("3 of 5 ready", model.readyText)
        assertEquals(0.6f, model.progress)
        assertTrue(model.ready)
        assertEquals(
            "All set. The last two are optional: battery helps on Samsung and Xiaomi, and location gives you local weather.",
            model.note,
        )
    }

    @Test
    fun onlyBatteryMissing() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.BATTERY))
        assertEquals("4 of 5 ready", model.readyText)
        assertEquals(0.8f, model.progress)
        assertTrue(model.ready)
        assertEquals("All set. Battery is optional, but helps on Samsung and Xiaomi.", model.note)
    }

    @Test
    fun onlyLocationMissing() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.LOCATION))
        assertEquals("4 of 5 ready", model.readyText)
        assertTrue(model.ready)
        assertEquals("All set. Location is optional, and gives you local weather.", model.note)
    }

    @Test
    fun everythingAllowed() {
        val model = setupModel(AndroidPermissions, emptyList())
        assertEquals("5 of 5 ready", model.readyText)
        assertEquals(1f, model.progress)
        assertTrue(model.ready)
        assertEquals("All set. Pip is ready when you are.", model.note)
    }

    @Test
    fun aMissingRequiredPermissionIsNotReady() {
        assertFalse(setupModel(AndroidPermissions, listOf(AlarmPermission.FULL_SCREEN)).ready)
    }

    @Test
    fun iosNeedsOnlyAlarmsAndOffersLocation() {
        assertEquals(listOf(AlarmPermission.ALARMS, AlarmPermission.LOCATION), IosPermissions)

        val waiting = setupModel(IosPermissions, IosPermissions)
        assertEquals("0 of 2 ready", waiting.readyText)
        assertFalse(waiting.ready)
        assertEquals("Alarms can’t ring until this is allowed.", waiting.note)

        val withoutLocation = setupModel(IosPermissions, listOf(AlarmPermission.LOCATION))
        assertEquals("1 of 2 ready", withoutLocation.readyText)
        assertTrue(withoutLocation.ready)
        assertEquals("All set. Location is optional, and gives you local weather.", withoutLocation.note)

        val allowed = setupModel(IosPermissions, emptyList())
        assertEquals("2 of 2 ready", allowed.readyText)
        assertTrue(allowed.ready)
        assertEquals("All set. Pip is ready when you are.", allowed.note)
    }
}
