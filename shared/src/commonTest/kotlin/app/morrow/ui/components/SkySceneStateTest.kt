package app.morrow.ui.components

import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import app.morrow.theme.mix
import app.morrow.weather.Sky
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The design canvas's artboards: morning 07:40, noon 12:30, afternoon 16:45, night 21:40, midnight 01:20.
class SkySceneStateTest {

    private fun scene(hour: Int, minute: Int) = (hour * 60 + minute).toFloat().let { skySceneState(it, ThemeKind.at(it.toInt())) }

    @Test
    fun morningSunHasOpenEyesAndRays() {
        val s = scene(7, 40)
        assertEquals(PipLook(PipMood.OPEN, rays = true), s.pip)
        assertEquals(0f, s.starAlpha)
    }

    @Test
    fun noonSunWearsShadesHighInTheSky() {
        val s = scene(12, 30)
        assertEquals(PipLook(PipMood.SHADES, rays = true), s.pip)
        assertTrue(s.pipY < 100f, "Pip should be near the top at noon, was ${s.pipY}")
    }

    @Test
    fun afternoonSunIsHappyAndSinking() {
        val s = scene(16, 45)
        assertEquals(PipLook(PipMood.HAPPY, rays = true), s.pip)
        assertTrue(s.pipY > scene(12, 30).pipY)
    }

    @Test
    fun nightShowsTheSleepingMoonWithANightcapAndLitWindow() {
        for (s in listOf(scene(21, 40), scene(1, 20))) {
            assertEquals(PipLook(PipMood.ASLEEP, moon = true, nightcap = true), s.pip)
            assertEquals(1f, s.starAlpha)
            assertEquals(1f, s.windowLit)
            assertEquals(0.35f, s.doorLit)
        }
    }

    @Test
    fun beforeSunriseThePipIsStillTheMoonEvenInTheMorningTheme() {
        val s = scene(5, 30)
        assertEquals(PipLook(PipMood.ASLEEP, moon = true, nightcap = true), s.pip)
        assertEquals(0.12f, s.windowLit)
    }

    @Test
    fun pipSitsOnTheHorizonAtSixAndAtItsHighestAtNoonAndMidnight() {
        assertEquals(238f, scene(6, 0).pipY, 0.01f)
        assertEquals(88f, scene(12, 0).pipY, 0.01f)
        assertEquals(88f, scene(0, 0).pipY, 0.01f)
    }

    @Test
    fun hillsAndCottageMixTheSkyWithTheThemeTints() {
        val minute = 7 * 60 + 40f
        val s = skySceneState(minute, ThemeKind.MORNING)
        val sky = SkyCurve.at(minute)
        val tints = ThemeKind.MORNING.colors()
        assertEquals(sky, s.sky)
        assertEquals(mix(sky, tints.hillTint, 0.38f), s.hillBack)
        assertEquals(mix(sky, tints.hillTint, 0.64f), s.hillMid)
        assertEquals(mix(sky, tints.houseTint, 0.8f), s.house)
    }

    @Test
    fun smokeRisesOnlyInTheMorningAndAtNight() {
        assertEquals(0.4f, scene(7, 40).smokeAlpha)
        assertEquals(0f, scene(12, 30).smokeAlpha)
        assertEquals(0f, scene(16, 45).smokeAlpha)
        assertEquals(0.4f, scene(21, 40).smokeAlpha)
    }

    @Test
    fun textOnTheSkyUsesDarkInkByDayAndStarlightAtNight() {
        assertEquals(ThemeKind.NOON.colors().text, scene(12, 30).ink)
        assertEquals(Illustration.starlight, scene(21, 40).ink)
        assertEquals(Illustration.starlightMuted, scene(21, 40).inkMuted)
    }

    private fun weather(hour: Int, minute: Int, sky: Sky?) =
        (hour * 60 + minute).toFloat().let { skySceneState(it, ThemeKind.at(it.toInt()), sky) }

    @Test
    fun withoutWeatherThereIsNoLayer() {
        assertNull(scene(7, 40).weather)
        assertNull(weather(7, 40, Sky.CLEAR).weather)
    }

    @Test
    fun theWeatherTintsTheSkyAndTheHillsAndCottageFollowIt() {
        val minute = 7 * 60 + 40f
        val s = skySceneState(minute, ThemeKind.MORNING, Sky.RAIN)
        val sky = skyTint(SkyCurve.at(minute), Sky.RAIN, isDay = true)
        val tints = ThemeKind.MORNING.colors()
        assertEquals(sky, s.sky)
        assertEquals(mix(sky, tints.hillTint, 0.38f), s.hillBack)
        assertEquals(mix(sky, tints.hillTint, 0.64f), s.hillMid)
        assertEquals(mix(sky, tints.houseTint, 0.8f), s.house)
        assertEquals(weatherLook(Sky.RAIN, sky, isDay = true), s.weather)
    }

    @Test
    fun atNightTheSkyIsTintedTowardTheNightColours() {
        val minute = 21 * 60 + 40f
        assertEquals(skyTint(SkyCurve.at(minute), Sky.SNOW, isDay = false), skySceneState(minute, ThemeKind.NIGHT, Sky.SNOW).sky)
    }

    @Test
    fun starsDimBehindAFullCoverOfCloud() {
        for (sky in listOf(Sky.CLOUDY, Sky.DRIZZLE, Sky.RAIN, Sky.STORM)) {
            assertEquals(0.2f, weather(21, 40, sky).starAlpha, 0.001f, "$sky")
        }
        for (sky in listOf(null, Sky.CLEAR, Sky.PARTLY_CLOUDY, Sky.FOG, Sky.SNOW)) {
            assertEquals(1f, weather(21, 40, sky).starAlpha, "$sky")
        }
    }

    @Test
    fun pipStaysUpInAnyWeatherButOnlyWearsShadesUnderABrightSky() {
        assertEquals(PipLook(PipMood.SHADES, rays = true), weather(12, 30, Sky.CLEAR).pip)
        assertEquals(PipLook(PipMood.SHADES, rays = true), weather(12, 30, Sky.PARTLY_CLOUDY).pip)
        for (sky in listOf(Sky.CLOUDY, Sky.FOG, Sky.DRIZZLE, Sky.RAIN, Sky.STORM, Sky.SNOW)) {
            val s = weather(12, 30, sky)
            assertEquals(PipLook(PipMood.OPEN, rays = true), s.pip, "$sky")
            assertEquals(scene(12, 30).pipY, s.pipY)
        }
        assertEquals(PipLook(PipMood.ASLEEP, moon = true, nightcap = true), weather(21, 40, Sky.RAIN).pip)
    }

    @Test
    fun textInkFollowsTheTintedSky() {
        // A storm darkens the golden afternoon enough for starlight ink.
        val clear = weather(16, 45, null)
        val storm = weather(16, 45, Sky.STORM)
        assertEquals(ThemeKind.AFTERNOON.colors().text, clear.ink)
        assertEquals(Illustration.starlight, storm.ink)
    }
}
