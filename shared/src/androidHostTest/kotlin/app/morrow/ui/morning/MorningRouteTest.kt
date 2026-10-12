package app.morrow.ui.morning

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.morrow.alarms.FakeClock
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.FakeWeatherCacheDao
import app.morrow.data.InMemoryPreferences
import app.morrow.data.MorrowSettings
import app.morrow.data.WeatherCacheDao
import app.morrow.data.WeatherCacheEntity
import app.morrow.places.CityCatalog
import app.morrow.places.CityRepository
import app.morrow.platform.FakeSpeaker
import app.morrow.platform.SpeechPurpose
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.ui.NoMotion
import app.morrow.weather.Coordinates
import app.morrow.weather.CurrentWeather
import app.morrow.weather.DayWeather
import app.morrow.weather.FakeLocationProvider
import app.morrow.weather.FetchException
import app.morrow.weather.Forecast
import app.morrow.weather.HourWeather
import app.morrow.weather.OpenMeteoJson
import app.morrow.weather.Sky
import app.morrow.weather.WeatherRepository
import app.morrow.weather.WeatherSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class MorningRouteTest {

    @get:Rule
    val compose = createComposeRule(effectContext = NoMotion)

    private val zone = TimeZone.of("Europe/London")
    private val now = LocalDateTime(2026, 10, 12, 7, 15).toInstant(zone)
    private val seven = LocalDateTime(2026, 10, 12, 7, 0).toInstant(zone)
    private val clock = FakeClock(now)
    private val settings = MorrowSettings(InMemoryPreferences())
    private val catalog = CityCatalog { "2643743\tLondon\t\tEngland\tUnited Kingdom\t51.5085\t-0.1257\tEurope/London\t8961989" }
    private val cities = CityRepository(FakeSavedCityDao(), catalog) { "Europe/London" }
    private val cache = FakeWeatherCacheDao()
    private val source = StubSource()
    private var speaker = FakeSpeaker()
    private var dao: WeatherCacheDao = cache
    private val location = FakeLocationProvider()
    private val started = mutableListOf<String>()
    private val london = Coordinates(51.51, -0.13)

    private val headline = "12° and rainy in London"
    private val detail = "High 14° · Low 8° · Rain until about 11:00"
    private val tip = "“Take an umbrella. It dries up this morning.”"
    private val spoken = "Good morning. It's 12 degrees and rainy in London, with a high of 14. Take an umbrella. It dries up this morning."
    private val replay = "Hear the weather again"

    /** Like Room, whose readers hear of a write a moment after it: here, only once the test calls [publish]. */
    private class LaggingCacheDao(private val stored: FakeWeatherCacheDao) : WeatherCacheDao {
        private val seen = MutableStateFlow(stored.rows.value.values.toList())

        override fun observeAll() = seen

        override suspend fun getAll() = stored.getAll()

        override suspend fun upsert(rows: List<WeatherCacheEntity>) = stored.upsert(rows)

        override suspend fun deleteAllExcept(keep: List<String>) = stored.deleteAllExcept(keep)

        fun publish() {
            seen.value = stored.rows.value.values.toList()
        }
    }

    private class StubSource : WeatherSource {
        var answer: (Coordinates) -> Forecast = { error("no answer set") }
        var offline = false
        var gate: CompletableDeferred<Unit>? = null
        var requests = 0

        override suspend fun forecast(places: List<Coordinates>): List<Forecast> {
            requests++
            gate?.await()
            if (offline) throw FetchException("offline")
            return places.map(answer)
        }
    }

    /** 12° and raining until 11:00 where the phone is, with a high of 14° and a low of 8°. */
    private fun rainy(fetchedAt: Instant, temperature: Double = 12.0) = Forecast(
        place = london,
        timeZone = "Europe/London",
        fetchedAt = fetchedAt,
        now = CurrentWeather(temperature, Sky.RAIN, isDay = true),
        today = DayWeather(high = 14.0, low = 8.0, rainChance = 80),
        hours = List(8) { HourWeather(seven + it.hours, temperature, if (it < 4) Sky.RAIN else Sky.CLOUDY, rainChance = if (it < 4) 80 else 10) },
    )

    private fun cached(forecast: Forecast) = runBlocking {
        cache.upsert(listOf(WeatherCacheEntity(forecast.place.key, OpenMeteoJson.encodeToString(Forecast.serializer(), forecast), forecast.fetchedAt.toEpochMilliseconds())))
    }

    private fun show(readAloud: Boolean = false, fontScale: Float = 1f) {
        runBlocking {
            settings.setReadWeatherAloud(readAloud)
            // The list loads on a real thread, which a test's virtual timeout would not wait for.
            catalog.cityForZone("Europe/London")
        }
        val weather = WeatherRepository(
            settings, cities, catalog, location, source, dao, clock, { "Europe/London" },
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MorrowTheme(ThemeKind.MORNING) {
                    MorningRoute(settings, weather, speaker, "GB", wokeAt = now, onStartDay = { started += it }, clock = clock, zone = zone)
                }
            }
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun cardIsShown() = compose.onAllNodesWithText(headline).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun todaysWeatherShowsAsACardAboveTheIntention() {
        cached(rainy(now - 10.minutes))
        show()

        waitForText(headline)
        compose.onNodeWithText(detail).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(tip).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("One small thing to look forward to today").performScrollTo().assertIsDisplayed()
        assertEquals(0, source.requests, "a forecast under half an hour old is left alone")
    }

    @Test
    fun withReadAloudOffPipNeverSpeaksAndThereIsNoReplayButton() {
        cached(rainy(now - 10.minutes))
        show(readAloud = false)

        waitForText(headline)
        compose.waitForIdle()

        assertEquals(emptyList(), speaker.spoken)
        assertEquals(0, speaker.stops)
        assertEquals(0, compose.onAllNodesWithContentDescription(replay).fetchSemanticsNodes().size)
    }

    @Test
    fun withReadAloudOnPipSpeaksOnceWhenTheCardAppears() {
        cached(rainy(now - 10.minutes))
        show(readAloud = true)

        waitForText(headline)
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }
        compose.waitForIdle()

        assertEquals(listOf(spoken to SpeechPurpose.MORNING_WEATHER), speaker.spoken)
    }

    @Test
    fun theReplayButtonSpeaksItAgain() {
        cached(rainy(now - 10.minutes))
        show(readAloud = true)
        waitForText(headline)
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }

        compose.onNodeWithContentDescription(replay).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.size == 2 }
        assertEquals(List(2) { spoken to SpeechPurpose.MORNING_WEATHER }, speaker.spoken)
    }

    @Test
    fun withNothingFromTodayTheCardIsHiddenAndPipStaysQuiet() {
        // Fetched at 22:00 last night: nine hours old, so not expired, but not today's either.
        cached(rainy(LocalDateTime(2026, 10, 11, 22, 0).toInstant(zone)))
        source.offline = true
        show(readAloud = true)

        compose.waitUntil(timeoutMillis = 5_000) { source.requests == 1 }
        compose.waitForIdle()

        assertTrue(!cardIsShown())
        assertEquals(0, compose.onAllNodesWithText("rainy", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription(replay).fetchSemanticsNodes().size)
        assertEquals(emptyList(), speaker.spoken)
        compose.onNodeWithText("One small thing to look forward to today").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aForecastFromTodayWhoseHoursHaveRunOutIsHiddenRatherThanShownAsNow() {
        val midnight = LocalDateTime(2026, 10, 12, 0, 0).toInstant(zone)
        val stale = rainy(midnight + 30.minutes, temperature = 6.0).copy(hours = List(4) { HourWeather(midnight + it.hours, 6.0, Sky.RAIN, 80) })
        cached(stale)
        source.offline = true
        show(readAloud = true)

        compose.waitUntil(timeoutMillis = 5_000) { source.requests == 1 }
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithText("6°", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription(replay).fetchSemanticsNodes().size)
        assertEquals(emptyList(), speaker.spoken)
    }

    @Test
    fun withNoForecastAtAllTheCardIsHidden() {
        source.offline = true
        show(readAloud = true)

        compose.waitUntil(timeoutMillis = 5_000) { source.requests == 1 }
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithText("High", substring = true).fetchSemanticsNodes().size)
        assertEquals(emptyList(), speaker.spoken)
    }

    @Test
    fun withWeatherOffTheCardIsHiddenAndNothingIsRequested() {
        cached(rainy(now - 10.minutes))
        runBlocking { settings.setWeatherEnabled(false) }
        show(readAloud = true)

        compose.waitForIdle()

        assertTrue(!cardIsShown())
        assertEquals(0, source.requests)
        assertEquals(0, location.calls)
        assertEquals(emptyList(), speaker.spoken)
    }

    @Test
    fun aFreshFetchReplacesTheCardInPlaceAndPipReadsTheNewOne() {
        val earlyHours = LocalDateTime(2026, 10, 12, 0, 30).toInstant(zone)
        cached(rainy(earlyHours, temperature = 6.0))
        source.answer = { rainy(clock.now, temperature = 12.0) }
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        show(readAloud = true)

        // Until the refresh lands, the card shows what today's cache says, and Pip waits.
        waitForText("6° and rainy in London")
        compose.waitForIdle()
        assertEquals(emptyList(), speaker.spoken)

        gate.complete(Unit)

        waitForText(headline)
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf(spoken to SpeechPurpose.MORNING_WEATHER), speaker.spoken)
        assertEquals(0, compose.onAllNodesWithText("6° and rainy in London").fetchSemanticsNodes().size)
    }

    @Test
    fun pipWaitsForTheNewForecastToReachTheCardBeforeReadingIt() {
        val earlyHours = LocalDateTime(2026, 10, 12, 0, 30).toInstant(zone)
        cached(rainy(earlyHours, temperature = 6.0))
        val lagging = LaggingCacheDao(cache)
        dao = lagging
        source.answer = { rainy(clock.now, temperature = 12.0) }
        show(readAloud = true)

        // The new forecast is stored, but the card hasn't heard of it yet.
        compose.waitUntil(timeoutMillis = 5_000) { cache.rows.value[london.key]?.fetchedAt == now.toEpochMilliseconds() }
        compose.waitForIdle()
        assertEquals(emptyList(), speaker.spoken)

        lagging.publish()

        waitForText(headline)
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf(spoken to SpeechPurpose.MORNING_WEATHER), speaker.spoken)
    }

    @Test
    fun startMyDayStopsPipAndHandsOverTheIntention() {
        speaker = FakeSpeaker(talkFor = 10.minutes)
        cached(rainy(now - 10.minutes))
        show(readAloud = true)
        waitForText(headline)
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }

        compose.onNodeWithContentDescription("One small thing to look forward to today").performScrollTo().performTextInput("coffee by the window ")
        compose.onNodeWithText("Start my day").performClick()

        assertEquals(listOf("coffee by the window"), started)
        assertEquals(listOf(SpeechPurpose.MORNING_WEATHER), speaker.stopped)
    }

    @Test
    fun oncePipHasFinishedStartMyDayStopsNothing() {
        cached(rainy(now - 10.minutes))
        show(readAloud = true)
        waitForText(headline)
        compose.waitUntil(timeoutMillis = 5_000) { speaker.spoken.isNotEmpty() }
        compose.waitForIdle()

        compose.onNodeWithText("Start my day").performClick()

        assertEquals(1, started.size)
        assertEquals(emptyList(), speaker.stopped, "a timer's note read meanwhile mustn't be cut off")
    }

    @Test
    fun startMyDayStaysPinnedWhileTheRestScrollsAtTwiceTheTextSize() {
        cached(rainy(now - 10.minutes))
        show(readAloud = true, fontScale = 2f)
        waitForText(headline)

        compose.onNodeWithText("Start my day").assertIsDisplayed()
        compose.onNodeWithContentDescription("One small thing to look forward to today").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start my day").assertIsDisplayed()
        compose.onNodeWithText(tip).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(replay).performScrollTo().assertIsDisplayed()
    }

    /** TalkBack reads a control by its text or description, so one with neither is unusable without sight. */
    @Test
    fun everyControlHasALabel() {
        cached(rainy(now - 10.minutes))
        show(readAloud = true)
        waitForText(headline)

        val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(controls.size >= 3, "the replay button, the intention field and Start my day")
        val unlabelled = controls.filter { node ->
            val config = node.config
            config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
        }
        assertEquals(emptyList(), unlabelled.map { it.config.toString() })
    }
}
