package app.morrow.ui.components

import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.isLightSky
import app.morrow.theme.mix
import app.morrow.weather.Sky
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CitySceneTest {

    private fun scene(hour: Int, minute: Int = 0) = cityScene(hour * 60 + minute)

    @Test
    fun theSkyFollowsTheCurve() {
        assertEquals(SkyCurve.at(7 * 60 + 40f), scene(7, 40).sky)
    }

    @Test
    fun theSunIsHighAtNoon() {
        val s = scene(12)
        assertTrue(s.sunUp)
        assertEquals(171f, s.bodyX, 0.01f)
        assertEquals(28f, s.bodyY, 0.01f)
        assertEquals(0f, s.starAlpha)
    }

    @Test
    fun theMoonIsHighAtMidnightWithStarsOut() {
        val s = scene(0)
        assertFalse(s.sunUp)
        assertEquals(171f, s.bodyX, 0.01f)
        assertEquals(28f, s.bodyY, 0.01f)
        assertEquals(1f, s.starAlpha)
    }

    @Test
    fun theSunRisesAtTheLeftHorizonAtSixAndSetsAtTheRightByEighteen() {
        val rise = scene(6)
        assertTrue(rise.sunUp)
        assertEquals(40f, rise.bodyX, 0.01f)
        assertEquals(104f, rise.bodyY, 0.01f)

        val almostSet = scene(17, 59)
        assertTrue(almostSet.sunUp)
        assertEquals(302f, almostSet.bodyX, 0.5f)
        assertEquals(104f, almostSet.bodyY, 0.5f)
    }

    @Test
    fun theMoonRisesAtTheLeftHorizonAtEighteenAndSetsAtTheRightByDawn() {
        val rise = scene(18)
        assertFalse(rise.sunUp)
        assertEquals(40f, rise.bodyX, 0.01f)
        assertEquals(104f, rise.bodyY, 0.01f)

        val almostSet = scene(5, 59)
        assertFalse(almostSet.sunUp)
        assertEquals(302f, almostSet.bodyX, 0.5f)
        assertEquals(104f, almostSet.bodyY, 0.5f)
    }

    @Test
    fun sunWearsShadesOnlyFromElevenToThree() {
        val withShades = (0 until 24 * 60).filter { cityScene(it).shades }
        assertEquals(11 * 60, withShades.first())
        assertEquals(15 * 60 - 1, withShades.last())
        assertEquals(4 * 60, withShades.size)
    }

    @Test
    fun starsShowOnlyAtNightAndFadeAtTheHorizon() {
        assertTrue((0 until 24 * 60).none { cityScene(it).sunUp && cityScene(it).starAlpha > 0f })
        assertTrue(scene(5, 50).starAlpha < 0.2f)
        assertTrue(scene(18, 10).starAlpha < 0.2f)
        assertTrue(scene(22).starAlpha > 0.9f)
    }

    @Test
    fun hillsMixTheSkyTowardTheNightGround() {
        val s = scene(12)
        assertEquals(mix(s.sky, Illustration.ground, 0.3f), s.hillBack)
        assertEquals(mix(s.sky, Illustration.ground, 0.5f), s.hillFront)
    }

    @Test
    fun minutesOutsideTheDayWrapRound() {
        assertEquals(scene(1), cityScene(25 * 60))
        assertEquals(scene(23), cityScene(-60))
    }

    @Test
    fun withoutWeatherTheSunAndMoonShowAndThereIsNoLayer() {
        assertEquals(1f, scene(12).bodyAlpha)
        assertEquals(1f, scene(0).bodyAlpha)
        assertNull(scene(12).weather)
    }

    @Test
    fun theWeatherTintsTheSkyByDayAndByNight() {
        assertEquals(skyTint(SkyCurve.at(12 * 60f), Sky.RAIN, isDay = true), cityScene(12 * 60, Sky.RAIN).sky)
        assertEquals(skyTint(SkyCurve.at(23 * 60f), Sky.RAIN, isDay = false), cityScene(23 * 60, Sky.RAIN).sky)
    }

    @Test
    fun theSunAndMoonShowInAClearOrBrightSkyAndHalfThroughFog() {
        for (minute in listOf(12 * 60, 0)) {
            assertEquals(1f, cityScene(minute, Sky.CLEAR).bodyAlpha)
            assertEquals(1f, cityScene(minute, Sky.PARTLY_CLOUDY).bodyAlpha)
            assertEquals(0.5f, cityScene(minute, Sky.FOG).bodyAlpha)
        }
    }

    @Test
    fun theSunAndMoonHideInHeavyWeather() {
        for (weather in listOf(Sky.CLOUDY, Sky.DRIZZLE, Sky.RAIN, Sky.STORM, Sky.SNOW)) {
            assertEquals(0f, cityScene(12 * 60, weather).bodyAlpha, "$weather by day")
            assertEquals(0f, cityScene(0, weather).bodyAlpha, "$weather by night")
        }
    }

    @Test
    fun starsShowOnlyUnderAClearOrBrightNightSky() {
        assertEquals(1f, cityScene(0, Sky.CLEAR).starAlpha)
        assertEquals(1f, cityScene(0, Sky.PARTLY_CLOUDY).starAlpha)
        for (weather in listOf(Sky.CLOUDY, Sky.FOG, Sky.DRIZZLE, Sky.RAIN, Sky.STORM, Sky.SNOW)) {
            assertEquals(0f, cityScene(0, weather).starAlpha, "$weather")
        }
    }

    @Test
    fun snowWhitensTheHills() {
        for (minute in listOf(12 * 60, 22 * 60 + 30)) {
            val s = cityScene(minute, Sky.SNOW)
            assertEquals(mix(s.sky, Illustration.snowHillBack, 0.5f), s.hillBack)
            assertEquals(mix(s.sky, Illustration.snowHillFront, 0.78f), s.hillFront)
        }
    }

    @Test
    fun otherWeatherKeepsTheHillsDarkUnderTheTintedSky() {
        for (weather in Sky.entries - Sky.SNOW) {
            val s = cityScene(12 * 60, weather)
            assertEquals(mix(s.sky, Illustration.ground, 0.3f), s.hillBack, "$weather")
            assertEquals(mix(s.sky, Illustration.ground, 0.5f), s.hillFront, "$weather")
        }
    }

    @Test
    fun theLayerIsDrawnForTheTintedSky() {
        assertNull(cityScene(12 * 60, Sky.CLEAR).weather)
        val rain = cityScene(23 * 60, Sky.RAIN)
        assertEquals(weatherLook(Sky.RAIN, rain.sky.isLightSky(), isDay = false), rain.weather)
        val partly = cityScene(10 * 60, Sky.PARTLY_CLOUDY)
        assertEquals(weatherLook(Sky.PARTLY_CLOUDY, partly.sky.isLightSky(), isDay = true), partly.weather)
    }
}
