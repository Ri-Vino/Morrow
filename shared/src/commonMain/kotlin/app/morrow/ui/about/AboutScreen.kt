package app.morrow.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.resources.Res
import app.morrow.theme.MorrowTheme
import app.morrow.theme.colors
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.Pip
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.pressable
import app.morrow.ui.settings.Card
import app.morrow.ui.settings.Note
import app.morrow.ui.settings.Section
import kotlinx.coroutines.CancellationException

private const val EXTERNAL_ICON = "M14 4h6v6M20 4l-9 9M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4"

private enum class AboutPage { MAIN, PRIVACY, TERMS, LICENCES, LICENCE }

/**
 * Legal & about, from Settings: who makes Morrow, its privacy policy, terms and licences. Every text is bundled, so it
 * all reads offline; only the GitHub links need a browser. [read] reads a text from the app's resources.
 */
@Composable
fun AboutRoute(
    version: String,
    onBack: () -> Unit,
    read: suspend (String) -> String = { Res.readBytes(it).decodeToString() },
) {
    var page by rememberSaveable { mutableStateOf(AboutPage.MAIN) }
    var licence by rememberSaveable { mutableIntStateOf(0) }
    val up: () -> Unit = {
        when (page) {
            AboutPage.MAIN -> onBack()
            AboutPage.LICENCE -> page = AboutPage.LICENCES
            else -> page = AboutPage.MAIN
        }
    }
    PlatformBackHandler(onBack = up)
    key(page) {
        when (page) {
            AboutPage.MAIN -> Page("Legal & about", "Back to settings", up) { AboutMain(version) { page = it } }
            AboutPage.PRIVACY -> Page("Privacy policy", "Back to legal & about", up) { LegalText(PRIVACY_FILE, read) }
            AboutPage.TERMS -> Page("Terms of service", "Back to legal & about", up) { LegalText(TERMS_FILE, read) }
            AboutPage.LICENCES -> Page("Open-source licences", "Back to legal & about", up) {
                LicenceList {
                    licence = it
                    page = AboutPage.LICENCE
                }
            }
            AboutPage.LICENCE -> {
                val shown = Licences[licence]
                Page(shown.name, "Back to licences", up) {
                    BasicText(shown.licence, style = MorrowTheme.type.bodyStrong.copy(color = MorrowTheme.colors.hi))
                    shown.text?.let { Blocks(legalBlocks(it)) }
                    shown.file?.let { LegalText(it, read) }
                }
            }
        }
    }
}

@Composable
private fun Page(title: String, backLabel: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MorrowTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).pressable(onBack).background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(Icons.Back, colors.text, size = 22.dp, strokeWidth = 2.4f, contentDescription = backLabel)
            }
            BasicText(
                title,
                style = MorrowTheme.type.title.copy(color = colors.text, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
            )
            Spacer(Modifier.size(44.dp))
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.AboutMain(version: String, onPage: (AboutPage) -> Unit) {
    val colors = MorrowTheme.colors
    val uri = LocalUriHandler.current
    // With no browser to open it in, the link's address still shows on the row.
    val open: (String) -> Unit = { url -> runCatching { uri.openUri(url) } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Pip(PipLook(PipMood.DELIGHTED), Modifier.size(88.dp).clearAndSetSemantics {})
        BasicText("Morrow", style = MorrowTheme.type.display.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        BasicText("Version $version", style = MorrowTheme.type.body.copy(color = colors.muted))
    }
    if ("beta" in version) {
        Note("This is a beta. Thanks for trying Morrow early. If anything goes wrong, please tell us.")
    }
    Section("Made by") {
        Card(selectable = false) {
            LinkRow(OWNER, OWNER_URL, divider = false, open)
            LinkRow("Source code", SOURCE_URL, divider = true, open)
            LinkRow("Report a problem", ISSUES_URL, divider = true, open)
        }
    }
    Section("Legal") {
        Card(selectable = false) {
            PageRow("Privacy policy", divider = false) { onPage(AboutPage.PRIVACY) }
            PageRow("Terms of service", divider = true) { onPage(AboutPage.TERMS) }
            PageRow("Open-source licences", divider = true) { onPage(AboutPage.LICENCES) }
        }
        Note("Morrow has no account, no ads and no tracking. Your alarms stay on your phone.")
    }
    BasicText(
        "© 2026 $OWNER · Apache License 2.0",
        style = MorrowTheme.type.caption.copy(color = colors.muted, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.LicenceList(onOpen: (Int) -> Unit) {
    Card(selectable = false) {
        Licences.forEachIndexed { i, licence -> PageRow(licence.name, divider = i > 0, detail = licence.licence) { onOpen(i) } }
    }
    Note("Thank you to everyone who made these.")
}

@Composable
private fun Divider(show: Boolean) {
    if (show) Box(Modifier.fillMaxWidth().height(1.dp).background(MorrowTheme.colors.line))
}

/** A row that opens [url] in the browser, showing the address so it can be read without one. */
@Composable
private fun LinkRow(title: String, url: String, divider: Boolean, open: (String) -> Unit) {
    val colors = MorrowTheme.colors
    Divider(divider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressable({ open(url) }, onClickLabel = "Open in your browser")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = MorrowTheme.type.button.copy(color = colors.text))
            BasicText(url.removePrefix("https://"), style = MorrowTheme.type.body.copy(color = colors.muted))
        }
        MorrowIcon(EXTERNAL_ICON, colors.muted, size = 20.dp, strokeWidth = 2.2f)
    }
}

@Composable
private fun PageRow(title: String, divider: Boolean, detail: String? = null, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    Divider(divider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = MorrowTheme.type.bodyLarge.copy(color = colors.text))
            detail?.let { BasicText(it, style = MorrowTheme.type.body.copy(color = colors.muted)) }
        }
        MorrowIcon(Icons.Forward, colors.muted, size = 20.dp, strokeWidth = 2.2f)
    }
}

/** The text in [file], once read. */
@Composable
private fun LegalText(file: String, read: suspend (String) -> String) {
    val blocks by produceState<List<LegalBlock>?>(initialValue = null, file) {
        value = try {
            legalBlocks(read(file))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            listOf(LegalBlock.Paragraph("This text couldn’t be opened. It’s also at $SOURCE_URL."))
        }
    }
    blocks?.let { Blocks(it) }
}

@Composable
private fun Blocks(blocks: List<LegalBlock>) {
    val colors = MorrowTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is LegalBlock.Heading -> BasicText(
                    block.text,
                    style = MorrowTheme.type.bodyStrong.copy(color = colors.text),
                    modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                )
                is LegalBlock.Paragraph -> BasicText(block.text, style = MorrowTheme.type.body.copy(color = colors.text))
                is LegalBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText("•", style = MorrowTheme.type.body.copy(color = colors.hi))
                    BasicText(block.text, style = MorrowTheme.type.body.copy(color = colors.text), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
