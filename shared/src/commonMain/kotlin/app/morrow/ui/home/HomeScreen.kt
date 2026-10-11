package app.morrow.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.theme.mix
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.MorrowToggle
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.Pip
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.SkyScene
import app.morrow.ui.components.pressable
import app.morrow.ui.components.skySceneState

class HomeActions(
    val onToggle: (id: Long, enabled: Boolean) -> Unit,
    val onOpenAlarm: (id: Long) -> Unit,
    val onNewAlarm: () -> Unit,
    val onTrySunrise: () -> Unit,
    val onSettings: () -> Unit,
    val onFixPermissions: () -> Unit,
)

@Composable
fun HomeScreen(model: HomeModel, theme: ThemeKind, minuteOfDay: Float, actions: HomeActions, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        // In the design the content starts 262 of the scene's 330 units down, over the hills.
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf(0.dp) }
        // Large text can make the header taller than the sky; then the content starts below it instead.
        val contentTop = maxOf(maxWidth * (262f / 390f), headerHeight + 16.dp)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SkyScene(minuteOfDay, theme)
            Header(model, theme, minuteOfDay, actions.onSettings, Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } })
            Column(
                modifier = Modifier
                    .padding(top = contentTop, start = 16.dp, end = 16.dp, bottom = 184.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                model.banner?.let { BlockedBanner(it, actions.onFixPermissions) }
                model.sleepCard?.let { SleepCardView(it, theme, actions.onTrySunrise) }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        "Alarms",
                        style = MorrowTheme.type.title.copy(color = colors.text),
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    BasicText(model.alarmCount, style = MorrowTheme.type.body.copy(color = colors.muted))
                }
                if (model.rows.isEmpty()) {
                    EmptyAlarms(actions.onNewAlarm)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        model.rows.forEach { AlarmRowView(it, actions) }
                    }
                }
            }
        }
        if (model.rows.isNotEmpty()) {
            PillButton(
                text = "New alarm",
                onClick = actions.onNewAlarm,
                background = colors.accent,
                content = colors.onAccent,
                icon = Icons.Plus,
                height = 56.dp,
                padding = PaddingValues(start = 16.dp, end = 24.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 104.dp)
                    .shadow(14.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow),
            )
        }
    }
}

@Composable
private fun Header(model: HomeModel, theme: ThemeKind, minuteOfDay: Float, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    val scene = skySceneState(minuteOfDay, theme)
    val ink by animateColorAsState(scene.ink, tween(1200))
    val inkMuted by animateColorAsState(scene.inkMuted, tween(1200))
    Column(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // With large text the theme chip and settings button wrap below the date rather than squeezing it.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                model.dateLabel.uppercase(),
                style = MorrowTheme.type.label.copy(color = inkMuted),
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.heightIn(min = 32.dp).background(colors.bg.copy(alpha = 0.7f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MorrowIcon(Icons.forTheme(theme), colors.text, size = 15.dp, strokeWidth = 2.2f)
                BasicText(theme.displayName, style = MorrowTheme.type.label.copy(color = colors.text, letterSpacing = MorrowTheme.type.caption.letterSpacing))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .pressable(onSettings, onClickLabel = "Open settings")
                    .background(colors.bg.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(Icons.Settings, colors.text, contentDescription = "Settings")
            }
            }
        }
        BasicText(
            model.greeting,
            style = MorrowTheme.type.display.copy(color = ink),
            modifier = Modifier.semantics { heading() },
        )
        model.lookingForward?.let {
            BasicText(
                it,
                style = MorrowTheme.type.bodyLarge.copy(color = inkMuted, fontFamily = MorrowTheme.type.title.fontFamily, fontStyle = FontStyle.Italic),
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

@Composable
private fun BlockedBanner(text: String, onFix: () -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(mix(colors.surface, colors.warn, 0.12f), shape)
            .border(1.dp, mix(colors.surface, colors.warn, 0.35f), shape)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(colors.warn, CircleShape), contentAlignment = Alignment.Center) {
            MorrowIcon(Icons.AlarmOff, colors.onWarn, strokeWidth = 2.4f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Your alarms can’t ring", style = MorrowTheme.type.button.copy(color = colors.text))
            BasicText(text, style = MorrowTheme.type.body.copy(color = colors.muted))
            PillButton("Allow in settings", onFix, colors.warn, colors.onWarn, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SleepCardView(card: SleepCard, theme: ThemeKind, onTrySunrise: () -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MorrowIcon(if (card.night) Icons.Moon else Icons.forTheme(theme), colors.hi, size = 18.dp)
            BasicText(card.lead, style = MorrowTheme.type.body.copy(color = colors.muted))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(card.big, style = MorrowTheme.type.display.copy(color = colors.hi), modifier = Modifier.weight(1f))
            PillButton(
                text = "Try sunrise",
                onClick = onTrySunrise,
                background = colors.surface2,
                content = colors.text,
                icon = Icons.TrySunrise,
                iconTint = colors.hi,
                padding = PaddingValues(start = 12.dp, end = 16.dp),
            )
        }
        BasicText(card.detail, style = MorrowTheme.type.body.copy(color = colors.muted))
    }
}

@Composable
private fun AlarmRowView(row: AlarmRow, actions: HomeActions) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val alpha by animateFloatAsState(if (row.enabled) 1f else 0.45f, tween(350))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .pressable({ actions.onOpenAlarm(row.id) }, onClickLabel = "Edit alarm")
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(alpha),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(row.time, style = MorrowTheme.type.display.copy(color = colors.text))
                MorrowIcon(if (row.sunUp) Icons.Sun else Icons.Moon, colors.hi)
            }
            BasicText(row.details, style = MorrowTheme.type.body.copy(color = colors.muted))
            row.zoneText?.let { text ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MorrowIcon(Icons.Globe, colors.hi, size = 14.dp, strokeWidth = 2.2f)
                    BasicText(text, style = MorrowTheme.type.label.copy(color = colors.hi, letterSpacing = MorrowTheme.type.caption.letterSpacing))
                }
            }
        }
        Row(
            modifier = Modifier
                .alpha(alpha)
                .background(colors.surface2, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorrowIcon(Icons.forMission(row.firstGame), colors.hi, size = 14.dp, strokeWidth = 2.2f)
            BasicText(row.games, style = MorrowTheme.type.label.copy(color = colors.hi, letterSpacing = MorrowTheme.type.caption.letterSpacing))
        }
        MorrowToggle(
            row.enabled,
            { actions.onToggle(row.id, it) },
            contentDescription = "${row.details} at ${row.time}" + (row.zoneText?.let { ", $it" } ?: ""),
        )
    }
}

@Composable
private fun EmptyAlarms(onNewAlarm: () -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pip(PipLook(PipMood.ASLEEP), Modifier.size(112.dp), snoreColor = colors.hi)
        BasicText("No alarms yet", style = MorrowTheme.type.title.copy(color = colors.text))
        BasicText(
            "Pip’s happy to let you sleep in. When you need a wake-up, pick a time on the sky dial.",
            style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
        )
        PrimaryButton("Set your first alarm", onNewAlarm, Modifier.padding(top = 16.dp), icon = Icons.Plus)
    }
}
