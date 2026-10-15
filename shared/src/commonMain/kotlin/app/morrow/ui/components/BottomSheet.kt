package app.morrow.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.ui.PlatformBackHandler
import kotlin.math.roundToInt

/**
 * A sheet rising from the bottom over a dimmed screen. Tapping the dimmed part, going back, or swiping the sheet down
 * calls [onClose]. [title] names the sheet for screen readers; the content brings its own heading.
 */
@Composable
fun BottomSheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val close by rememberUpdatedState(onClose)
    val density = LocalDensity.current
    val closeDistance = with(density) { 120.dp.toPx() }
    val closeSpeed = with(density) { 1000.dp.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    // A downward drag the content can't scroll any further pulls the sheet down; letting go far enough, or quickly
    // enough, closes it, and otherwise it settles back.
    val swipeDown = remember(closeDistance, closeSpeed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || pulled <= 0f) return Offset.Zero
                val used = maxOf(available.y, -pulled)
                pulled += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                pulled += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f) return Velocity.Zero
                if (pulled > closeDistance || available.y > closeSpeed) {
                    close()
                } else {
                    animate(pulled, 0f) { value, _ -> pulled = value }
                }
                return available
            }
        }
    }
    PlatformBackHandler(onBack = onClose)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Illustration.scrim).pressable(onClose).semantics { contentDescription = "Close" })
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, pulled.roundToInt()) }
                .fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(colors.bg, shape)
                .semantics { paneTitle = title }
                .nestedScroll(swipeDown)
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
