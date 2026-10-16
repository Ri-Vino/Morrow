package app.morrow.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.morrow.theme.Illustration
import app.morrow.theme.SkyCurve
import app.morrow.theme.mix
import app.morrow.weather.Sky
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WeatherLayerTest {

    private val noon = SkyCurve.at(12 * 60f)
    private val midnight = SkyCurve.at(0f)

    private class Tint(val weather: Sky, val amount: Float, val day: Color, val night: Color)

    // The spec's table, and the canvas's colours to mix toward.
    private val tints = listOf(
        Tint(Sky.PARTLY_CLOUDY, 0.12f, Illustration.tintGreyDay, Illustration.tintGreyNight),
        Tint(Sky.CLOUDY, 0.45f, Illustration.tintGreyDay, Illustration.tintGreyNight),
        Tint(Sky.FOG, 0.55f, Illustration.tintFogDay, Illustration.tintFogNight),
        Tint(Sky.DRIZZLE, 0.4f, Illustration.tintGreyDay, Illustration.tintGreyNight),
        Tint(Sky.RAIN, 0.55f, Illustration.tintGreyDay, Illustration.tintGreyNight),
        Tint(Sky.STORM, 0.72f, Illustration.tintStormDay, Illustration.tintStormNight),
        Tint(Sky.SNOW, 0.45f, Illustration.tintSnowDay, Illustration.tintSnowNight),
    )

    @Test
    fun aClearSkyIsLeftAlone() {
        assertEquals(noon, skyTint(noon, Sky.CLEAR, isDay = true))
        assertEquals(midnight, skyTint(midnight, Sky.CLEAR, isDay = false))
    }

    @Test
    fun eachWeatherMixesTheSkyByTheSpecsAmountTowardItsDayOrNightColour() {
        for (tint in tints) {
            assertEquals(mix(noon, tint.day, tint.amount), skyTint(noon, tint.weather, isDay = true), "${tint.weather} by day")
            assertEquals(mix(midnight, tint.night, tint.amount), skyTint(midnight, tint.weather, isDay = false), "${tint.weather} by night")
        }
        assertEquals(Sky.entries.toSet() - Sky.CLEAR, tints.map { it.weather }.toSet())
    }

    @Test
    fun theTimeOfDayStillShowsThroughEvenAStorm() {
        assertNotEquals(skyTint(noon, Sky.STORM, isDay = true), skyTint(SkyCurve.at(16 * 60f), Sky.STORM, isDay = true))
    }

    @Test
    fun aClearSkyHasNothingToDraw() {
        assertNull(weatherLook(Sky.CLEAR, lightSky = true, isDay = true))
    }

    @Test
    fun cloudsAreAsThickAsTheCanvasHasThem() {
        val alphas = (Sky.entries - Sky.CLEAR).associateWith { look(it).cloudAlpha }
        assertEquals(
            mapOf(
                Sky.PARTLY_CLOUDY to 0.9f, Sky.CLOUDY to 1f, Sky.FOG to 0.35f, Sky.DRIZZLE to 1f,
                Sky.RAIN to 1f, Sky.SNOW to 0.9f, Sky.STORM to 1f,
            ),
            alphas,
        )
    }

    @Test
    fun cloudsAreBrightInAPartlyCloudySkyGreyInHeavierWeatherAndDarkInAStorm() {
        assertEquals(Illustration.cloudBrightDay, look(Sky.PARTLY_CLOUDY).cloudColor)
        assertEquals(Illustration.cloudBrightNight, look(Sky.PARTLY_CLOUDY, isDay = false).cloudColor)
        assertEquals(Illustration.cloudDay, look(Sky.RAIN).cloudColor)
        assertEquals(Illustration.cloudNight, look(Sky.SNOW, isDay = false).cloudColor)
        assertEquals(Illustration.stormCloudDay, look(Sky.STORM).cloudColor)
        assertEquals(Illustration.stormCloudNight, look(Sky.STORM, isDay = false).cloudColor)
    }

    @Test
    fun eachWeatherDrawsOnlyItsOwnLayers() {
        for (weather in Sky.entries - Sky.CLEAR) {
            val look = look(weather)
            assertEquals(weather in listOf(Sky.DRIZZLE, Sky.RAIN, Sky.STORM), look.drops, "$weather drops")
            assertEquals(weather == Sky.SNOW, look.snow, "$weather snow")
            assertEquals(weather == Sky.FOG, look.fog, "$weather fog")
            assertEquals(weather == Sky.STORM, look.lightning, "$weather lightning")
        }
    }

    @Test
    fun rainIsDarkOnALightSkyAndPaleOnADarkOne() {
        assertEquals(Illustration.rainOnLight, weatherLook(Sky.RAIN, lightSky = true, isDay = true)?.dropColor)
        assertEquals(Illustration.rainOnDark, weatherLook(Sky.RAIN, lightSky = false, isDay = false)?.dropColor)
    }

    @Test
    fun fogIsWhiteByDayAndLilacAtNight() {
        assertEquals(Illustration.fogDay, look(Sky.FOG).fogColor)
        assertEquals(Illustration.fogNight, look(Sky.FOG, isDay = false).fogColor)
    }

    @Test
    fun theDropsAndFlakesSitWhereTheCanvasPutsThem() {
        val home = WeatherShapes.Home
        assertEquals(30, home.drops.size)
        assertEquals(Offset(8f, 70f), home.drops.first())
        assertEquals(Offset(73f, 75f), home.drops[5])
        assertEquals(Offset(385f, 243f), home.drops.last())
        assertEquals(0.12f, home.dropDelays[6], 0.001f)
        assertEquals(26, home.flakes.size)
        assertEquals(Offset(115f, 87f), home.flakes[7])
        assertEquals(Offset(385f, 75f), home.flakes.last())
        assertEquals(0.05f, home.flakeDelays.last(), 0.001f)

        val card = WeatherShapes.Card
        assertEquals(14, card.drops.size)
        assertEquals(Offset(43f, 67f), card.drops[1])
        assertEquals(Offset(319f, 61f), card.drops.last())
        assertEquals(0.41f, card.dropDelays.last(), 0.001f)
        assertEquals(16, card.flakes.size)
        assertEquals(Offset(203f, 87f), card.flakes[9])
        assertEquals(Offset(329f, 63f), card.flakes.last())
        assertEquals(2.75f, card.flakeDelays.last(), 0.001f)
    }

    @Test
    fun homesThirdCloudSitsJustBelowPipSoPipPeeksOverIt() {
        val clouds = WeatherShapes.Home.clouds
        assertEquals(listOf(false, false, true), clouds.map { it.underPip })
        assertEquals(18f, clouds.last().y)
        assertFalse(WeatherShapes.Card.clouds.any { it.underPip })
    }

    private fun look(weather: Sky, isDay: Boolean = true) = weatherLook(weather, lightSky = isDay, isDay)!!
}
