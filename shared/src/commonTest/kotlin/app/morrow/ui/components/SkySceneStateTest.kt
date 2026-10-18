package app.morrow.ui.components

import app.morrow.theme.Holiday
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import app.morrow.theme.contrastRatio
import app.morrow.theme.mix
import app.morrow.weather.Feel
import app.morrow.weather.Sky
import app.morrow.weather.Warmth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
        assertEquals(weatherLook(Sky.RAIN, s.lightSky, isDay = true), s.weather)
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
        // A storm darkens the sunset enough for starlight ink.
        val clear = weather(18, 30, null)
        val storm = weather(18, 30, Sky.STORM)
        assertEquals(ThemeKind.AFTERNOON.colors().text, clear.ink)
        assertEquals(Illustration.starlight, storm.ink)
    }

    @Test
    fun inDarkModeTheDaySkyTonesDownTowardsTheDimGroundAndKeepsDarkInk() {
        val minute = 12 * 60 + 30f
        val s = skySceneState(minute, ThemeKind.NOON, dim = true)
        val tints = ThemeKind.NOON.colors(dim = true)
        val sky = mix(SkyCurve.at(minute), tints.bg, 0.2f)
        assertEquals(sky, s.sky)
        assertEquals(mix(sky, tints.hillTint, 0.38f), s.hillBack)
        assertEquals(mix(sky, tints.houseTint, 0.8f), s.house)
        assertEquals(ThemeKind.NOON.colors().text, s.ink)
        // The dimmer sky is too close to the muted ink, which deepens towards the full ink to stay readable.
        assertNotEquals(ThemeKind.NOON.colors().muted, s.inkMuted)
        assertTrue(contrastRatio(s.inkMuted, sky) >= 4.5f)
    }

    @Test
    fun wordsOverTheSkyStayReadableFromDawnToDusk() {
        for (dim in listOf(false, true)) {
            for (minute in 5 * 60..19 * 60 step 2) {
                val s = skySceneState(minute.toFloat(), ThemeKind.at(minute), dim = dim)
                val behind = if (s.backing.alpha == 0f) s.sky else mix(s.sky, s.backing.copy(alpha = 1f), s.backing.alpha)
                val at = "${minute / 60}:${(minute % 60).toString().padStart(2, '0')}${if (dim) " dim" else ""}"
                // The greeting is large text, but the weather line beside it is small and shares its ink.
                assertTrue(contrastRatio(s.ink, behind) >= 3f, "greeting at $at")
                assertTrue(contrastRatio(s.ink, behind) >= 4.5f, "weather line at $at")
                assertTrue(contrastRatio(s.inkMuted, behind) >= 4.5f, "date at $at")
            }
        }
    }

    @Test
    fun theBackingOnlyShowsAroundSunriseAndSunset() {
        for ((hour, minute) in listOf(7 to 40, 12 to 30, 16 to 45, 21 to 40, 1 to 20)) {
            assertEquals(0f, scene(hour, minute).backing.alpha, "$hour:$minute")
        }
        assertTrue(scene(6, 16).backing.alpha > 0f)
        assertTrue(scene(18, 40).backing.alpha > 0f)
    }

    @Test
    fun rainFollowsTheInk() {
        for (minute in 0 until 24 * 60 step 2) {
            val s = skySceneState(minute.toFloat(), ThemeKind.at(minute), Sky.RAIN)
            assertEquals(if (s.lightSky) ThemeKind.at(minute).colors().text else Illustration.starlight, s.ink, "$minute")
            assertEquals(if (s.lightSky) Illustration.rainOnLight else Illustration.rainOnDark, s.weather?.dropColor, "$minute")
        }
    }

    @Test
    fun darkModeLeavesTheNightSceneAlone() {
        val minute = 21 * 60 + 40f
        assertEquals(skySceneState(minute, ThemeKind.NIGHT), skySceneState(minute, ThemeKind.NIGHT, dim = true))
    }

    @Test
    fun aMildStillDayAddsNothing() {
        val s = skySceneState(7 * 60 + 40f, ThemeKind.MORNING, feel = Feel.Mild)
        assertEquals(PipOutside(), s.pip.outside)
        assertEquals(ClimateLook.None, s.climate)
        assertEquals(skySceneState(7 * 60 + 40f, ThemeKind.MORNING), s)
    }

    @Test
    fun theColdBringsFrostAndPipWrapsUp() {
        val cold = skySceneState(7 * 60 + 40f, ThemeKind.MORNING, feel = Feel(Warmth.COLD, windy = false))
        assertTrue(cold.pip.outside.scarf)
        assertTrue(!cold.pip.outside.beanie)
        val freezing = skySceneState(7 * 60 + 40f, ThemeKind.MORNING, feel = Feel(Warmth.FREEZING, windy = false))
        assertTrue(freezing.pip.outside.scarf && freezing.pip.outside.beanie && freezing.pip.outside.breath)
        assertTrue(freezing.climate.frost > cold.climate.frost)
        assertTrue(freezing.pip.outside.shiverEvery < cold.pip.outside.shiverEvery)
    }

    @Test
    fun theHeatPutsPipInShadesWheneverTheSunIsUp() {
        val hot = skySceneState(7 * 60 + 40f, ThemeKind.MORNING, Sky.CLOUDY, feel = Feel(Warmth.HOT, windy = false))
        assertEquals(PipMood.SHADES, hot.pip.mood)
        assertTrue(hot.pip.outside.sweat && !hot.pip.outside.fan)
        val scorching = skySceneState(12 * 60 + 30f, ThemeKind.NOON, feel = Feel(Warmth.SCORCHING, windy = false))
        assertTrue(scorching.pip.outside.fan)
        assertTrue(scorching.climate.heat > hot.climate.heat)
        // The moon sleeps through a hot night in its nightcap.
        val night = skySceneState(21 * 60 + 40f, ThemeKind.NIGHT, feel = Feel(Warmth.HOT, windy = false))
        assertEquals(PipMood.ASLEEP, night.pip.mood)
    }

    @Test
    fun windBlowsLeavesUnlessItsSnowing() {
        val windy = Feel(Warmth.MILD, windy = true)
        val rain = skySceneState(7 * 60 + 40f, ThemeKind.MORNING, Sky.RAIN, feel = windy)
        assertTrue(rain.climate.leaves)
        assertTrue(rain.weather!!.windy)
        assertTrue(rain.pip.outside.windswept)
        assertTrue(!skySceneState(7 * 60 + 40f, ThemeKind.MORNING, Sky.SNOW, feel = windy).climate.leaves)
    }

    @Test
    fun halloweenLeansThePurpleWayAndLightsTheCottage() {
        val minute = 12 * 60 + 30f
        val plain = skySceneState(minute, ThemeKind.NOON)
        val spooky = skySceneState(minute, ThemeKind.NOON, holiday = Holiday.HALLOWEEN)
        assertNotEquals(plain.sky, spooky.sky)
        assertEquals(1f, spooky.windowLit)
        assertEquals(Holiday.HALLOWEEN, spooky.pip.holiday)
        assertEquals(Holiday.HALLOWEEN, spooky.holiday)
    }

    @Test
    fun christmasLeavesTheSkyItsOwnColour() {
        val minute = 12 * 60 + 30f
        val festive = skySceneState(minute, ThemeKind.NOON, holiday = Holiday.CHRISTMAS)
        assertEquals(skySceneState(minute, ThemeKind.NOON).sky, festive.sky)
        assertEquals(Holiday.CHRISTMAS, festive.pip.holiday)
    }
}
