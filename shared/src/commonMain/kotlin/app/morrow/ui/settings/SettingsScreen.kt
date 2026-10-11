package app.morrow.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.domain.Difficulty
import app.morrow.domain.VolumeRamp
import app.morrow.domain.WakeSound
import app.morrow.platform.AlarmPermission
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind
import app.morrow.theme.colors
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.MorrowToggle
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.TileRows
import app.morrow.ui.components.pressable
import app.morrow.ui.firstrun.icon

class SettingsChoices(val difficulty: Difficulty, val sound: WakeSound, val ramp: VolumeRamp)

class SettingsActions(
    val onBack: () -> Unit,
    val onFollowClock: (Boolean) -> Unit,
    val onPinTheme: (ThemeKind) -> Unit,
    val onDifficulty: (Difficulty) -> Unit,
    val onSound: (WakeSound) -> Unit,
    val onRamp: (VolumeRamp) -> Unit,
    val onAllow: (AlarmPermission) -> Unit,
    /** Only in debug builds. */
    val onDeveloperTools: (() -> Unit)?,
)

@Composable
fun SettingsScreen(model: SettingsModel, choices: SettingsChoices, actions: SettingsActions, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).pressable(actions.onBack).background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(Icons.Back, colors.text, size = 22.dp, strokeWidth = 2.4f, contentDescription = "Back to alarms")
            }
            BasicText(
                "Settings",
                style = MorrowTheme.type.title.copy(color = colors.text, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Spacer(Modifier.size(44.dp))
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Theme") {
                FollowClockRow(model, actions.onFollowClock)
                TileRows(ThemeKind.entries, Modifier.selectableGroup()) { kind ->
                    ThemeTile(kind, selected = kind == model.shownTheme) { actions.onPinTheme(kind) }
                }
            }
            Section("Mission difficulty") {
                SegmentedControl(Difficulty.entries, choices.difficulty, Difficulty::displayName, actions.onDifficulty)
                Note(model.difficultyLevel)
                Note(model.difficultyDescription)
            }
            Section("Alarm sound") {
                Card {
                    WakeSound.entries.forEachIndexed { i, sound ->
                        RadioRow(sound.displayName, selected = sound == choices.sound, divider = i > 0) { actions.onSound(sound) }
                    }
                }
            }
            Section("Volume ramp") {
                SegmentedControl(VolumeRamp.entries, choices.ramp, VolumeRamp::displayName, actions.onRamp)
                Note("Starts quiet and gets louder, so the sunrise does the waking.")
            }
            Section("Permissions") {
                Card(selectable = false) {
                    model.permissions.forEachIndexed { i, status -> PermissionRow(status, divider = i > 0) { actions.onAllow(status.permission) } }
                }
            }
            actions.onDeveloperTools?.let { SecondaryButton("Developer tools", it) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    "Morrow · No ads, no accounts",
                    style = MorrowTheme.type.caption.copy(color = colors.muted, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
                BasicText(
                    "City data © GeoNames (CC BY 4.0)",
                    style = MorrowTheme.type.caption.copy(color = colors.muted, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            title.uppercase(),
            style = MorrowTheme.type.label.copy(color = MorrowTheme.colors.muted),
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Note(text: String) {
    BasicText(text, style = MorrowTheme.type.body.copy(color = MorrowTheme.colors.muted), modifier = Modifier.padding(horizontal = 8.dp))
}

@Composable
private fun Card(selectable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .then(if (selectable) Modifier.selectableGroup() else Modifier),
        content = content,
    )
}

@Composable
private fun FollowClockRow(model: SettingsModel, onFollowClock: (Boolean) -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .toggleable(model.followClock, role = Role.Switch, onValueChange = onFollowClock)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Follow the clock", style = MorrowTheme.type.button.copy(color = colors.text))
            BasicText(model.followDescription, style = MorrowTheme.type.body.copy(color = colors.muted))
        }
        // The whole row is the switch for screen readers, so this one only shows its state.
        MorrowToggle(model.followClock, onFollowClock, contentDescription = "Follow the clock", modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun RowScope.ThemeTile(kind: ThemeKind, selected: Boolean, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    val swatch = kind.colors()
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (selected) colors.hi else colors.surface)
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 80.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = kind.displayName }
            .background(if (selected) colors.surface2 else colors.surface, shape)
            .border(2.dp, border, shape)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .border(1.dp, swatch.line, CircleShape)
                .padding(1.dp)
                .background(swatch.bg, CircleShape)
                .padding(4.dp)
                .background(swatch.accent, CircleShape),
        )
        BasicText(
            kind.shortName,
            style = MorrowTheme.type.bodyStrong.copy(color = if (selected) colors.text else colors.muted, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun <T> SegmentedControl(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val background by animateColorAsState(if (on) colors.accent else Color.Transparent)
            val content by animateColorAsState(if (on) colors.onAccent else colors.muted)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .selectable(on, role = Role.RadioButton) { onSelect(option) }
                    .background(background, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label(option), style = MorrowTheme.type.bodyStrong.copy(color = content, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, divider: Boolean, onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(24.dp).border(2.dp, if (selected) colors.hi else colors.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).background(colors.hi, CircleShape))
        }
        BasicText(label, style = MorrowTheme.type.bodyLarge.copy(color = colors.text), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PermissionRow(status: PermissionStatus, divider: Boolean, onAllow: () -> Unit) {
    val colors = MorrowTheme.colors
    val permission = status.permission
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MorrowIcon(permission.icon, colors.hi)
        BasicText(permission.title, style = MorrowTheme.type.bodyLarge.copy(color = colors.text), modifier = Modifier.weight(1f))
        if (status.granted) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${permission.title} allowed" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MorrowIcon(Icons.Check, colors.hi, size = 16.dp, strokeWidth = 3f)
                BasicText("Allowed", style = MorrowTheme.type.bodyStrong.copy(color = colors.hi))
            }
        } else {
            val required = permission.required
            PillButton(
                text = "Allow",
                onClick = onAllow,
                background = if (required) colors.warn else colors.surface2,
                content = if (required) colors.onWarn else colors.text,
                modifier = Modifier.semantics { contentDescription = "Allow ${permission.title.lowercase()}" },
            )
        }
    }
}
