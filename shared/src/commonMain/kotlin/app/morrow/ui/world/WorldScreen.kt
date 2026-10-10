package app.morrow.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme

@Composable
fun WorldScreen(modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    Column(
        modifier = modifier.fillMaxSize().background(colors.bg).statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("World", style = MorrowTheme.type.display.copy(color = colors.text), modifier = Modifier.semantics { heading() })
        BasicText("Your cities will live here.", style = MorrowTheme.type.body.copy(color = colors.muted))
    }
}
