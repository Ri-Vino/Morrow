package app.morrow.platform

import java.util.Locale
import java.util.MissingResourceException

/**
 * The voices from [all] that Pip can use on a phone set to [phone]: the same language, installed, and working offline,
 * best first. That is higher quality, then the phone's own country, then name.
 */
internal fun installedVoices(all: Collection<EngineVoice>, phone: Locale): List<VoiceInfo> {
    val ownCountry = phone.country
    val usable = all
        .filter { it.installed && !it.needsNetwork && sameLanguage(it.locale, phone) }
        .distinctBy { it.name }
        .sortedWith(
            compareByDescending<EngineVoice> { it.quality }
                .thenByDescending { ownCountry.isNotEmpty() && it.locale.country.equals(ownCountry, ignoreCase = true) }
                .thenBy { it.name },
        )
    val perLocale = usable.groupingBy { it.locale.toLanguageTag() }.eachCount()
    val counted = mutableMapOf<String, Int>()
    return usable.map { voice ->
        val tag = voice.locale.toLanguageTag()
        val label = languageLabel(voice.locale, phone)
        val number = (counted[tag] ?: 0) + 1
        counted[tag] = number
        // Engines name voices with codes like "en-gb-x-gba-local", so a voice is "English (UK)", numbered if it has siblings.
        VoiceInfo(voice.name, if (perLocale.getValue(tag) > 1) "$label · Voice $number" else label, label)
    }
}

/** "English (UK)": [voice]'s language and region, in the language of [display]. */
internal fun languageLabel(voice: Locale, display: Locale): String {
    val language = voice.getDisplayLanguage(display).ifEmpty { voice.toLanguageTag() }.replaceFirstChar { it.titlecase(display) }
    val region = when (voice.country) {
        "" -> return language
        "GB" -> "UK"
        "US" -> "US"
        else -> voice.getDisplayCountry(display).ifEmpty { voice.country }
    }
    return "$language ($region)"
}

/** Engines don't agree on two- and three-letter codes, so compare either. */
private fun sameLanguage(a: Locale, b: Locale): Boolean {
    if (a.language.isEmpty() || b.language.isEmpty()) return false
    if (a.language.equals(b.language, ignoreCase = true)) return true
    val first = threeLetters(a)
    return first != null && first == threeLetters(b)
}

private fun threeLetters(locale: Locale): String? = try {
    locale.isO3Language.ifEmpty { null }
} catch (_: MissingResourceException) {
    null
}
