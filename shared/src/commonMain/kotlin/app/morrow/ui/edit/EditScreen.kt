package app.morrow.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.SkyDial
import app.morrow.ui.components.pressable
import app.morrow.ui.home.displayName
import kotlinx.datetime.DayOfWeek

class EditActions(
    val onChange: (Alarm) -> Unit,
    val onBack: () -> Unit,
    val onDelete: () -> Unit,
    val onPreview: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun EditScreen(draft: Alarm, model: EditModel, actions: EditActions, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(model, actions)
            SkyDial(
                minuteOfDay = draft.hour * 60 + draft.minute,
                onMinuteChange = { actions.onChange(draft.at(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            TimeRow(model, onNudge = { actions.onChange(draft.nudged(it)) })
            BasicText(
                model.untilText,
                style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Section("Repeat") { DayChips(draft.days) { actions.onChange(draft.copy(days = it)) } }
            Section("How should Pip wake you?") {
                MissionTiles(draft.mission) { actions.onChange(draft.copy(mission = it)) }
                BasicText(
                    model.missionDescription,
                    style = MorrowTheme.type.body.copy(color = colors.muted),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Row(
            modifier = Modifier.navigationBarsPadding().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton("Preview", actions.onPreview, Modifier.weight(1f))
            PrimaryButton("Save alarm", actions.onSave, Modifier.weight(2f))
        }
    }
}

@Composable
private fun Header(model: EditModel, actions: EditActions) {
    val colors = MorrowTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Back, "Back to alarms", actions.onBack)
        BasicText(
            model.title,
            style = MorrowTheme.type.title.copy(color = colors.text, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (model.canDelete) {
            RoundIconButton(Icons.Trash, "Delete alarm", actions.onDelete, tint = colors.muted)
        } else {
            Box(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun TimeRow(model: EditModel, onNudge: (Int) -> Unit) {
    val colors = MorrowTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        RoundIconButton(Icons.Minus, "Five minutes earlier", { onNudge(-DIAL_STEP_MINUTES) }, strokeWidth = 2.6f)
        Column(
            modifier = Modifier.width(176.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(model.daypart.uppercase(), style = MorrowTheme.type.label.copy(color = colors.hi))
            BasicText(model.time, style = MorrowTheme.type.displayLarge.copy(color = colors.text, fontFeatureSettings = "tnum"))
        }
        RoundIconButton(Icons.Plus, "Five minutes later", { onNudge(DIAL_STEP_MINUTES) }, strokeWidth = 2.6f)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
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
private fun DayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val colors = MorrowTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            val name = day.name.lowercase().replaceFirstChar(Char::uppercase)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = name }
                    .toggleable(on, role = Role.Checkbox) { onChange(if (it) days + day else days - day) }
                    .background(if (on) colors.accent else colors.surface, CircleShape)
                    .then(if (on) Modifier.border(2.dp, colors.hi, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(name.take(1), style = MorrowTheme.type.button.copy(color = if (on) colors.onAccent else colors.muted))
            }
        }
    }
}

@Composable
private fun MissionTiles(selected: MissionType, onSelect: (MissionType) -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MissionType.entries.forEach { mission ->
            val on = mission == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .selectable(on, role = Role.RadioButton) { onSelect(mission) }
                    .background(if (on) colors.surface2 else colors.surface, shape)
                    .border(2.dp, if (on) colors.hi else colors.surface, shape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                MorrowIcon(Icons.forMission(mission), if (on) colors.hi else colors.muted, size = 24.dp)
                BasicText(mission.displayName, style = MorrowTheme.type.bodyStrong.copy(color = if (on) colors.text else colors.muted))
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: String,
    description: String,
    onClick: () -> Unit,
    tint: Color = MorrowTheme.colors.text,
    strokeWidth: Float = 2.4f,
) {
    Box(
        modifier = Modifier.size(44.dp).pressable(onClick).background(MorrowTheme.colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        MorrowIcon(icon, tint, size = 22.dp, strokeWidth = strokeWidth, contentDescription = description)
    }
}
