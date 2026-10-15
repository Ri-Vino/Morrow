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
        assertEquals(6, AndroidPermissions.size)
        assertEquals(AlarmPermission.LOCATION, AndroidPermissions.last())
        assertFalse(AlarmPermission.LOCATION.required)
        assertEquals("Location", AlarmPermission.LOCATION.title)
        assertEquals("Optional. For your local weather; a city works too.", AlarmPermission.LOCATION.description)
    }

    @Test
    fun nothingAllowedYet() {
        val model = setupModel(AndroidPermissions, AndroidPermissions)
        assertEquals("0 of 6 ready", model.readyText)
        assertEquals(0f, model.progress)
        assertFalse(model.ready)
        assertEquals("Alarms can’t ring until the first three are allowed.", model.note)
    }

    @Test
    fun requiredOnesAllowedIsReadyWithoutTheOptionalOnes() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.BATTERY, AlarmPermission.LOCATION))
        assertEquals("4 of 6 ready", model.readyText)
        assertEquals(4f / 6, model.progress)
        assertTrue(model.ready)
        assertEquals(
            "All set. The last two are optional: battery helps on Samsung and Xiaomi, and location gives you local weather.",
            model.note,
        )
    }

    @Test
    fun onlyBatteryMissing() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.BATTERY))
        assertEquals("5 of 6 ready", model.readyText)
        assertEquals(5f / 6, model.progress)
        assertTrue(model.ready)
        assertEquals("All set. Battery is optional, but helps on Samsung and Xiaomi.", model.note)
    }

    @Test
    fun onlyLocationMissing() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.LOCATION))
        assertEquals("5 of 6 ready", model.readyText)
        assertTrue(model.ready)
        assertEquals("All set. Location is optional, and gives you local weather.", model.note)
    }

    @Test
    fun onlyOverAppsMissing() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.OVER_APPS))
        assertTrue(model.ready)
        assertFalse(AlarmPermission.OVER_APPS.required)
        assertEquals("All set. Showing over other apps is optional, but fills the screen while you use your phone.", model.note)
    }

    @Test
    fun severalOptionalMissing() {
        val model = setupModel(AndroidPermissions, listOf(AlarmPermission.OVER_APPS, AlarmPermission.BATTERY, AlarmPermission.LOCATION))
        assertTrue(model.ready)
        assertEquals("All set. The rest are optional, and each one helps.", model.note)
    }

    @Test
    fun everythingAllowed() {
        val model = setupModel(AndroidPermissions, emptyList())
        assertEquals("6 of 6 ready", model.readyText)
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
