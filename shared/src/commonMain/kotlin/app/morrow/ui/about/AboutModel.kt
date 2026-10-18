package app.morrow.ui.about

/** Who makes Morrow, and where to find them. */
internal const val OWNER = "Rivino"
internal const val OWNER_URL = "https://github.com/Ri-Vino"
internal const val SOURCE_URL = "https://github.com/Ri-Vino/Morrow"
internal const val ISSUES_URL = "https://github.com/Ri-Vino/Morrow/issues"

internal const val PRIVACY_FILE = "files/legal/privacy.md"
internal const val TERMS_FILE = "files/legal/terms.md"

/** Something Morrow ships under its own licence: [file] is the licence's text in the app's resources, or [text] says it. */
internal class Licence(val name: String, val licence: String, val file: String? = null, val text: String? = null)

private const val APACHE = "files/licenses/apache-2.0.txt"

internal val Licences = listOf(
    Licence("Morrow", "Apache License 2.0 · © 2026 $OWNER", file = APACHE),
    Licence("Fraunces", "SIL Open Font License 1.1", file = "files/licenses/fraunces-OFL.txt"),
    Licence("Figtree", "SIL Open Font License 1.1", file = "files/licenses/figtree-OFL.txt"),
    Licence(
        "Kotlin, Compose and AndroidX",
        "Apache License 2.0",
        file = APACHE,
        text = "Morrow is built with the Kotlin standard library, kotlinx.coroutines, kotlinx.serialization and " +
            "kotlinx-datetime; Compose Multiplatform and Jetpack Compose; AndroidX Activity, Core, Lifecycle, Room, " +
            "SQLite, DataStore and CameraX; ZXing; and the libraries they use. Each is under this licence.",
    ),
    Licence(
        "SQLite",
        "Public domain",
        text = "Morrow keeps its alarms in SQLite, which its authors have placed in the public domain. https://sqlite.org",
    ),
    Licence(
        "City data",
        "CC BY 4.0 · GeoNames",
        text = "City names, positions and populations come from GeoNames (https://www.geonames.org), under the " +
            "Creative Commons Attribution 4.0 licence (https://creativecommons.org/licenses/by/4.0/). Morrow ships a " +
            "trimmed copy of the list.",
    ),
    Licence(
        "Weather and place search",
        "CC BY 4.0 · Open-Meteo",
        text = "Weather data by Open-Meteo.com (https://open-meteo.com), under the Creative Commons Attribution 4.0 " +
            "licence (https://creativecommons.org/licenses/by/4.0/).",
    ),
    Licence(
        "Classical recordings",
        "Public domain · Musopen",
        text = "The Classical alarm sounds are the opening seconds of recordings that Musopen and its musicians " +
            "released into the public domain, from Wikimedia Commons: Grieg's Morning Mood, Satie's Gymnopédie No. 1, " +
            "Bach's Goldberg Aria, Haydn's The Lark, Mozart's Figaro overture and Mendelssohn's Italian Symphony. " +
            "Thanks to Musopen (https://musopen.org).",
    ),
)

/** A piece of a legal text, as the page shows it. */
internal sealed interface LegalBlock {
    data class Heading(val text: String) : LegalBlock

    data class Paragraph(val text: String) : LegalBlock

    data class Bullet(val text: String) : LegalBlock
}

/**
 * Splits [text] into what a page shows: "## " lines are headings, "- " lines bullets, and the rest paragraphs, each
 * ending at a blank line. A "# " title is left out, as the page has its own. Lines within a paragraph are joined, so
 * a hard-wrapped licence reflows to the screen.
 */
internal fun legalBlocks(text: String): List<LegalBlock> {
    val blocks = mutableListOf<LegalBlock>()
    val paragraph = StringBuilder()
    fun endParagraph() {
        if (paragraph.isNotEmpty()) blocks += LegalBlock.Paragraph(paragraph.toString())
        paragraph.clear()
    }
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> endParagraph()
            line.startsWith("## ") -> {
                endParagraph()
                blocks += LegalBlock.Heading(line.removePrefix("## "))
            }
            line.startsWith("# ") -> endParagraph()
            line.startsWith("- ") -> {
                endParagraph()
                blocks += LegalBlock.Bullet(line.removePrefix("- "))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    endParagraph()
    return blocks
}
