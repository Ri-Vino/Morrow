package app.morrow.ui.firstrun

import app.morrow.platform.AlarmPermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupModelTest {

    @Test
    fun nothingAllowedYet() {
        val model = setupModel(AlarmPermission.entries)
        assertEquals("0 of 4 ready", model.readyText)
        assertEquals(0f, model.progress)
        assertFalse(model.ready)
        assertEquals("Alarms can’t ring until the first three are allowed.", model.note)
    }

    @Test
    fun requiredOnesAllowedIsReadyEvenWithoutBattery() {
        val model = setupModel(listOf(AlarmPermission.BATTERY))
        assertEquals("3 of 4 ready", model.readyText)
        assertEquals(0.75f, model.progress)
        assertTrue(model.ready)
        assertEquals("All set. Battery is optional, but helps on Samsung and Xiaomi.", model.note)
    }

    @Test
    fun everythingAllowed() {
        val model = setupModel(emptyList())
        assertEquals("4 of 4 ready", model.readyText)
        assertEquals(1f, model.progress)
        assertTrue(model.ready)
        assertEquals("All set. Pip is ready when you are.", model.note)
    }

    @Test
    fun aMissingRequiredPermissionIsNotReady() {
        assertFalse(setupModel(listOf(AlarmPermission.FULL_SCREEN)).ready)
    }
}
