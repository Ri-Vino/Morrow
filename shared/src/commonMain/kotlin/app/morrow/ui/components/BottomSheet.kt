package app.morrow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.PlatformBackHandler

/**
 * A sheet rising from the bottom over a dimmed screen. Tapping the dimmed part, or going back, calls [onClose].
 * [title] names the sheet for screen readers; the content brings its own heading.
 */
@Composable
fun BottomSheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    PlatformBackHandler(onBack = onClose)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Illustration.scrim).pressable(onClose).semantics { contentDescription = "Close" })
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(colors.bg, shape)
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(colors.line, CircleShape))
            content()
        }
    }
}
