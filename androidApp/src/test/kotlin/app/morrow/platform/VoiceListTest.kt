package app.morrow.platform

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceListTest {

    private val british = Locale.forLanguageTag("en-GB")

    private fun voice(
        name: String,
        tag: String = "en-GB",
        quality: Int = 400,
        needsNetwork: Boolean = false,
        installed: Boolean = true,
    ) = EngineVoice(name, Locale.forLanguageTag(tag), quality, needsNetwork, installed)

    @Test
    fun keepsOnlyVoicesForThePhonesLanguage() {
        val voices = installedVoices(listOf(voice("en-gb"), voice("en-us", "en-US"), voice("fr-fr", "fr-FR"), voice("de-de", "de-DE")), british)

        assertEquals(listOf("en-gb", "en-us"), voices.map { it.id })
    }

    @Test
    fun leavesOutVoicesThatNeedTheNetworkOrAreNotInstalled() {
        val voices = installedVoices(
            listOf(
                voice("local"),
                voice("online", needsNetwork = true),
                voice("missing", installed = false),
            ),
            british,
        )

        assertEquals(listOf("local"), voices.map { it.id })
    }

    @Test
    fun putsHigherQualityFirstThenThePhonesCountryThenTheName() {
        val voices = installedVoices(
            listOf(
                voice("b-gb-normal"),
                voice("a-us-normal", "en-US"),
                voice("c-us-high", "en-US", quality = 500),
                voice("a-gb-normal"),
                voice("d-gb-low", quality = 200),
            ),
            british,
        )

        assertEquals(listOf("c-us-high", "a-gb-normal", "b-gb-normal", "a-us-normal", "d-gb-low"), voices.map { it.id })
    }

    @Test
    fun aPhoneWithNoCountryJustSortsByQualityThenName() {
        val voices = installedVoices(listOf(voice("b"), voice("a", "en-US"), voice("c", quality = 500)), Locale.forLanguageTag("en"))

        assertEquals(listOf("c", "a", "b"), voices.map { it.id })
    }

    @Test
    fun namesVoicesByTheirLanguageAndNumbersThoseThatShareOne() {
        val voices = installedVoices(listOf(voice("gb-1"), voice("gb-2"), voice("us-1", "en-US"), voice("au-1", "en-AU", quality = 100)), british)

        assertEquals(
            listOf("English (UK) · Voice 1", "English (UK) · Voice 2", "English (US)", "English (Australia)"),
            voices.map { it.name },
        )
        assertEquals(listOf("English (UK)", "English (UK)", "English (US)", "English (Australia)"), voices.map { it.language })
    }

    @Test
    fun aVoiceWithoutACountryIsNamedByItsLanguage() {
        assertEquals("English", languageLabel(Locale.forLanguageTag("en"), british))
    }

    @Test
    fun labelsAreInThePhonesLanguage() {
        assertEquals("Français (Canada)", languageLabel(Locale.forLanguageTag("fr-CA"), Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun threeLetterAndTwoLetterLanguageCodesAreTheSameLanguage() {
        val voices = installedVoices(listOf(voice("eng", tag = "eng-GB")), british)

        assertEquals(listOf("eng"), voices.map { it.id })
    }

    @Test
    fun listsAVoiceNameOnlyOnce() {
        val voices = installedVoices(listOf(voice("same"), voice("same")), british)

        assertEquals(listOf("English (UK)"), voices.map { it.name })
    }

    @Test
    fun noVoicesMeansAnEmptyList() {
        assertEquals(emptyList(), installedVoices(emptyList(), british))
    }
}
