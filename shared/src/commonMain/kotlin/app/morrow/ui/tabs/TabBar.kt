package app.morrow.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.ui.PlatformBackHandler
import app.morrow.ui.components.MorrowIcon
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: String) {
    ALARMS("Alarms", "M12 6a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM12 10v3l2 1.5M5 3L2 6M19 3l3 3"),
    WORLD("World", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z"),
    TIMERS("Timers", "M10 2h4M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM12 9v4l2.5 2"),
}

/** Keeps the chosen tab through rotation; by name, since not every platform can save an enum. */
val TabSaver = Saver<Tab, String>(save = { it.name }, restore = { Tab.valueOf(it) })

/**
 * The three main screens above the bar, which is docked to the bottom of the screen so nothing scrolls under it. Back
 * from World or Timers returns to [Tab.ALARMS].
 */
@Composable
fun TabHost(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    /** True while a sheet over the tab needs the bottom of the screen for its own buttons. */
    barHidden: Boolean = false,
    world: @Composable () -> Unit,
    timers: @Composable () -> Unit,
    alarms: @Composable () -> Unit,
) {
    PlatformBackHandler(enabled = selected != Tab.ALARMS, onBack = { onSelect(Tab.ALARMS) })
    Column(modifier.fillMaxSize().background(MorrowTheme.colors.bg)) {
        // The bar takes the navigation bar's space, so the screen above it needn't leave room for it.
        Box(Modifier.weight(1f).fillMaxWidth().then(if (barHidden) Modifier else Modifier.consumeWindowInsets(WindowInsets.navigationBars))) {
            when (selected) {
                Tab.ALARMS -> alarms()
                Tab.WORLD -> world()
                Tab.TIMERS -> timers()
            }
        }
        if (!barHidden) TabBar(selected, onSelect)
    }
}

/** The bar that switches between the main screens, docked along the bottom edge and over the navigation bar. */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // The pill's two ends, in tabs from the left. The end leading the way moves first and the other follows, so the
    // pill stretches towards the tab picked and settles there.
    val start = remember { Animatable(selected.ordinal.toFloat()) }
    val end = remember { Animatable(selected.ordinal + 1f) }
    val reduceMotion = MorrowTheme.reduceMotion
    LaunchedEffect(selected) {
        val right = selected.ordinal > start.value
        val target = selected.ordinal.toFloat()
        if (reduceMotion) {
            start.snapTo(target)
            end.snapTo(target + 1f)
            return@LaunchedEffect
        }
        launch { start.animateTo(target, if (right) Trailing else Leading) }
        launch { end.animateTo(target + 1f, if (right) Leading else Trailing) }
    }
    // A neutral pill: the brand colour is kept for the chosen tab's icon and label.
    val pill = colors.surface2
    val outline = colors.line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surface, shape)
            .topEdge(colors.line)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(min = 52.dp)
            .drawBehind {
                val gap = TAB_GAP.toPx()
                val tab = (size.width - gap * (Tab.entries.size - 1)) / Tab.entries.size
                val left = start.value * (tab + gap)
                val right = end.value * (tab + gap) - gap
                val radius = CornerRadius(PILL_RADIUS.toPx())
                val line = 1.dp.toPx()
                drawRoundRect(pill, Offset(left, 0f), Size(right - left, size.height), radius)
                drawRoundRect(
                    outline,
                    Offset(left + line / 2, line / 2),
                    Size(right - left - line, size.height - line),
                    radius,
                    style = Stroke(line),
                )
            }
            .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = Tab.entries.size) },
        horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            TabItem(tab, index, tab == selected, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, index: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    val content by animateColorAsState(if (selected) colors.hi else colors.muted, tween(TAB_FADE_MILLIS))
    // Big text no longer fits beside the icon in a third of the bar, so it goes underneath.
    val stacked = LocalDensity.current.fontScale > 1.3f
    val itemModifier = Modifier
        .weight(1f)
        .heightIn(min = 52.dp)
        .selectable(selected, role = Role.Tab, onClick = onClick)
        .semantics { collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1) }
        .padding(horizontal = 4.dp)
    if (stacked) {
        Column(itemModifier, Arrangement.Center, Alignment.CenterHorizontally) {
            TabContent(tab, content, selected)
        }
    } else {
        Row(itemModifier, Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Alignment.CenterVertically) {
            TabContent(tab, content, selected)
        }
    }
}

@Composable
private fun TabContent(tab: Tab, content: Color, selected: Boolean) {
    // The chosen tab's icon is filled in, as the platform's tab bars do.
    MorrowIcon(tab.icon, content, size = 20.dp, strokeWidth = 2.2f, fill = if (selected) content.copy(alpha = SELECTED_FILL) else null)
    BasicText(tab.label, style = MorrowTheme.type.bodyStrong.copy(color = content), maxLines = 1)
}

private const val TAB_FADE_MILLIS = 220
private val TAB_GAP = 4.dp

private const val SELECTED_FILL = 0.3f

/** The selected tab's pill; its outline marks it where the fill alone is too faint against the bar. */
private val PILL_RADIUS = 26.dp
private val Leading = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
private val Trailing = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)

/** A hairline along the top, where the bar meets the screen. */
private fun Modifier.topEdge(color: Color) = drawBehind {
    val corner = 28.dp.toPx()
    drawLine(color, Offset(corner, 0f), Offset(size.width - corner, 0f), 1.dp.toPx())
}
