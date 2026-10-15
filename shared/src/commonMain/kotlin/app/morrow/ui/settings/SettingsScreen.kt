package app.morrow.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.morrow.theme.colors
import app.morrow.ui.components.BottomSheet
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.MorrowToggle
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.pressable
import app.morrow.ui.firstrun.icon
import app.morrow.weather.Units
import app.morrow.weather.WeatherPlaceMode

private const val SPEAKER_ICON = "M4 9v6h4l5 4V5L8 9H4zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"
private const val PLAY_ICON = "M9 7.5v9l7-4.5z"

class SettingsChoices(val difficulty: Difficulty, val sound: WakeSound, val ramp: VolumeRamp)

class SettingsActions(
    val onBack: () -> Unit,
    val onDifficulty: (Difficulty) -> Unit,
    val onSound: (WakeSound) -> Unit,
    val onRamp: (VolumeRamp) -> Unit,
    val onAllow: (AlarmPermission) -> Unit,
    /** Only in debug builds. */
    val onDeveloperTools: (() -> Unit)?,
    val onWeatherEnabled: (Boolean) -> Unit = {},
    val onWeatherMode: (WeatherPlaceMode) -> Unit = {},
    val onPickHomeCity: () -> Unit = {},
    val onUnits: (Units) -> Unit = {},
    val onReadAloud: (Boolean) -> Unit = {},
    /** Pip's voice was picked in the sheet: null for the phone's default. */
    val onPickVoice: (String?) -> Unit = {},
    val onHearSample: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    model: SettingsModel,
    choices: SettingsChoices,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    weather: WeatherSection? = null,
    voice: VoiceSection? = null,
) {
    var pickingVoice by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        SettingsContent(model, choices, actions, weather, voice, onPickVoice = { pickingVoice = true })
        if (pickingVoice && voice != null) {
            VoicePickerSheet(
                voice,
                onPick = { id ->
                    pickingVoice = false
                    actions.onPickVoice(id)
                },
                onClose = { pickingVoice = false },
            )
        }
    }
}

@Composable
private fun SettingsContent(
    model: SettingsModel,
    choices: SettingsChoices,
    actions: SettingsActions,
    weather: WeatherSection?,
    voice: VoiceSection?,
    onPickVoice: () -> Unit,
) {
    val colors = MorrowTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
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
            if (weather != null) Section("Weather") { WeatherSettings(weather, actions) }
            if (voice != null) Section("Voice") { VoiceSettings(voice, actions.onHearSample, onPickVoice) }
            Section("Mission difficulty") {
                SegmentedControl(Difficulty.entries, choices.difficulty, Difficulty::displayName, actions.onDifficulty)
                Note(model.difficultyLevel)
                Note(model.difficultyDescription)
            }
            Section("Default alarm sound") {
                Card {
                    WakeSound.entries.forEachIndexed { i, sound ->
                        RadioRow(sound.displayName, selected = sound == choices.sound, divider = i > 0) { actions.onSound(sound) }
                    }
                }
                Note("Alarms ring with this unless you pick a sound for one when you edit it.")
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
                    "Weather data by Open-Meteo.com (CC BY 4.0)",
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

/** A switch row in a card of its own, or with [framed] false, a row inside a card. */
@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, framed: Boolean = true) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(if (framed) Modifier.background(colors.surface, shape).border(1.dp, colors.line, shape) else Modifier)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title, style = MorrowTheme.type.button.copy(color = colors.text))
            BasicText(description, style = MorrowTheme.type.body.copy(color = colors.muted))
        }
        // The whole row is the switch for screen readers, so this one only shows its state.
        MorrowToggle(checked, onChange, contentDescription = title, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun ColumnScope.WeatherSettings(weather: WeatherSection, actions: SettingsActions) {
    val colors = MorrowTheme.colors
    ToggleRow("Show the weather", "On Home, World and the Morning screen.", weather.enabled, actions.onWeatherEnabled)
    if (!weather.enabled) return
    SegmentedControl(WeatherPlaceMode.entries, weather.mode, WeatherPlaceMode::displayName, actions.onWeatherMode)
    Note(weather.description)
    Card(selectable = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .pressable(actions.onPickHomeCity)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("Home city", style = MorrowTheme.type.bodyLarge.copy(color = colors.text), modifier = Modifier.weight(1f))
            BasicText(weather.homeCityName, style = MorrowTheme.type.bodyStrong.copy(color = colors.hi, textAlign = TextAlign.End))
            MorrowIcon(Icons.Forward, colors.muted, size = 20.dp, strokeWidth = 2.2f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("Units", style = MorrowTheme.type.bodyLarge.copy(color = colors.text), modifier = Modifier.weight(1f))
            SegmentedControl(
                options = Units.entries,
                selected = weather.units,
                label = Units::symbol,
                onSelect = actions.onUnits,
                modifier = Modifier.width(132.dp),
                background = colors.bg,
                border = null,
                description = { if (it == Units.CELSIUS) "Celsius" else "Fahrenheit" },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        ToggleRow(
            "Pip reads it out when you wake",
            "After your wake-up games. Off unless you turn it on.",
            weather.readAloud,
            actions.onReadAloud,
            framed = false,
        )
    }
}

@Composable
private fun ColumnScope.VoiceSettings(voice: VoiceSection, onHearSample: () -> Unit, onPickVoice: () -> Unit) {
    val colors = MorrowTheme.colors
    Card(selectable = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .pressable(onPickVoice)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MorrowIcon(SPEAKER_ICON, colors.hi, size = 22.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("Pip’s voice", style = MorrowTheme.type.button.copy(color = colors.text))
                BasicText(voice.current, style = MorrowTheme.type.body.copy(color = colors.muted))
            }
            MorrowIcon(Icons.Forward, colors.muted, size = 20.dp, strokeWidth = 2.2f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .pressable(onHearSample)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The design's play triangle is solid; a thick round stroke draws one.
            MorrowIcon(PLAY_ICON, colors.hi, size = 20.dp, strokeWidth = 4.5f)
            BasicText("Hear a sample", style = MorrowTheme.type.button.copy(color = colors.hi))
        }
    }
    Note("Reads timer notes and the morning weather. It uses your phone’s own voices, so nothing is sent anywhere.")
}

@Composable
private fun VoicePickerSheet(voice: VoiceSection, onPick: (String?) -> Unit, onClose: () -> Unit) {
    val colors = MorrowTheme.colors
    BottomSheet(title = "Pip’s voice", onClose = onClose) {
        BasicText(
            "Pip’s voice",
            style = MorrowTheme.type.title.copy(color = colors.text),
            modifier = Modifier.semantics { heading() },
        )
        Card {
            voice.choices.forEachIndexed { i, choice ->
                RadioRow(choice.title, selected = choice.selected, divider = i > 0, detail = choice.detail) { onPick(choice.id) }
            }
        }
        voice.emptyNote?.let { Note(it) }
    }
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    background: Color = MorrowTheme.colors.surface,
    border: Color? = MorrowTheme.colors.line,
    /** What TalkBack says for an option, when its label alone reads badly. */
    description: ((T) -> String)? = null,
) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .background(background, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val fill by animateColorAsState(if (on) colors.accent else Color.Transparent)
            val content by animateColorAsState(if (on) colors.onAccent else colors.muted)
            val outline by animateColorAsState(if (on) colors.hi else Color.Transparent)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .selectable(on, role = Role.RadioButton) { onSelect(option) }
                    .then(if (description != null) Modifier.semantics { contentDescription = description(option) } else Modifier)
                    .background(fill, CircleShape)
                    .border(2.dp, outline, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label(option), style = MorrowTheme.type.bodyStrong.copy(color = content, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, divider: Boolean, detail: String? = null, onClick: () -> Unit) {
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(label, style = MorrowTheme.type.bodyLarge.copy(color = colors.text))
            detail?.let { BasicText(it, style = MorrowTheme.type.body.copy(color = colors.muted)) }
        }
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
