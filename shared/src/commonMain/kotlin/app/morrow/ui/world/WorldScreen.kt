package app.morrow.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.morrow.theme.MorrowTheme
import app.morrow.ui.components.BottomSheet
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import app.morrow.ui.components.weatherIcon

class WorldActions(
    val onAddCity: () -> Unit,
    val onOpenCity: (Long) -> Unit,
    val onCloseCity: () -> Unit,
    val onMakeHome: (Long) -> Unit,
    val onRemove: (Long) -> Unit,
    /** Moves a city [by] places in the list, negative for up. */
    val onMove: (id: Long, by: Int) -> Unit,
    /** Opens Edit for a new alarm on that city's clock. */
    val onNewAlarm: (Long) -> Unit = {},
)

/** The World tab: a card for each saved city, and the sheet for [sheet] when a card has been tapped. */
@Composable
fun WorldScreen(model: WorldModel, sheet: CitySheet?, actions: WorldActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        // Behind the sheet, the cities are hidden from TalkBack as well as dimmed.
        WorldContent(model, actions, Modifier.semantics { if (sheet != null) hideFromAccessibility() })
        if (sheet != null) CitySheetView(sheet, actions)
    }
}

@Composable
private fun WorldContent(model: WorldModel, actions: WorldActions, modifier: Modifier) {
    val colors = MorrowTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(actions.onAddCity)
        CityCards(model.cards, actions)
        model.footer?.let {
            BasicText(
                it,
                style = MorrowTheme.type.caption.copy(color = colors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun Header(onAddCity: () -> Unit) {
    val colors = MorrowTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            "World",
            style = MorrowTheme.type.display.copy(color = colors.text),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .pressable(onAddCity)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MorrowIcon(Icons.Plus, colors.text, size = 22.dp, strokeWidth = 2.6f, contentDescription = "Add a city")
        }
    }
}

/**
 * The cities in the user's order. Each can be moved by a long press and drag, or by TalkBack's "Move up" and "Move down".
 * While a card is held, the order shown follows the finger straight away and each swap is saved as it happens.
 */
@Composable
private fun CityCards(cards: List<WorldCard>, actions: WorldActions) {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(cards)
    val latestActions by rememberUpdatedState(actions)
    var dragging by remember { mutableStateOf<Long?>(null) }
    var order by remember { mutableStateOf<List<Long>?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    // While dragging, show the order the finger has made, provided it still names the same cities.
    val shown = order?.takeIf { it.sorted() == cards.map { card -> card.id }.sorted() }
        ?.let { ids -> ids.map { id -> cards.first { it.id == id } } }
        ?: cards

    // Swaps with a neighbour once the card has been dragged over half its height.
    val drag = { dy: Float, step: Float ->
        val id = dragging
        val ids = order
        if (id != null && ids != null) {
            offset += dy
            val index = ids.indexOf(id)
            val by = when {
                offset > step / 2f && index < ids.lastIndex -> 1
                offset < -step / 2f && index > 0 -> -1
                else -> 0
            }
            if (by != 0) {
                order = ids.toMutableList().apply { add(index + by, removeAt(index)) }
                offset -= by * step
                latestActions.onMove(id, by)
            }
        }
    }
    val gap = with(LocalDensity.current) { 12.dp.toPx() }

    shown.forEach { card ->
        key(card.id) {
            val held = dragging == card.id
            CityCard(
                name = card.name,
                isHome = card.isHome,
                relative = card.relative,
                clock = card.clock,
                minuteOfDay = card.minute,
                weather = card.weather,
                onClick = { actions.onOpenCity(card.id) },
                modifier = Modifier
                    .zIndex(if (held) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (held) offset else 0f
                        val lift = if (held) 1.02f else 1f
                        scaleX = lift
                        scaleY = lift
                    }
                    .semantics {
                        customActions = listOfNotNull(
                            if (card.canMoveUp) CustomAccessibilityAction("Move up") { actions.onMove(card.id, -1); true } else null,
                            if (card.canMoveDown) CustomAccessibilityAction("Move down") { actions.onMove(card.id, 1); true } else null,
                        )
                    }
                    .pointerInput(card.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = card.id
                                order = latest.map { it.id }
                                offset = 0f
                            },
                            onDrag = { pointer, amount ->
                                pointer.consume()
                                drag(amount.y, size.height + gap)
                            },
                            onDragEnd = { dragging = null; order = null },
                            onDragCancel = { dragging = null; order = null },
                        )
                    },
            )
        }
    }
}

@Composable
private fun CitySheetView(sheet: CitySheet, actions: WorldActions) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val clock = @Composable {
        BasicText(sheet.clock, style = type.display.copy(color = colors.text, fontFeatureSettings = "tnum"))
    }
    BottomSheet(title = sheet.name, onClose = actions.onCloseCity) {
        val name = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(sheet.name, style = type.title.copy(color = colors.text), modifier = Modifier.semantics { heading() })
                BasicText(sheet.place, style = type.body.copy(color = colors.muted))
            }
        }
        if (largeText()) {
            name(Modifier)
            clock()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                name(Modifier.weight(1f))
                clock()
            }
        }
        BasicText(
            listOfNotNull(sheet.relative, sheet.conditions).joinToString(" · "),
            style = type.body.copy(color = colors.muted),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (sheet.hours.isNotEmpty()) NextHours(sheet.hours)
        sheet.day?.let { BasicText(it, style = type.body.copy(color = colors.muted), modifier = Modifier.padding(horizontal = 8.dp)) }
        PrimaryButton(sheet.newAlarm, { actions.onNewAlarm(sheet.id) })
        if (sheet.isHome) {
            BasicText(
                "This is your home city. Weather uses it when location is off.",
                style = type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val makeHome = @Composable { modifier: Modifier ->
                PillButton("Make home city", { actions.onMakeHome(sheet.id) }, colors.surface, colors.text, modifier.fillMaxWidth(), height = 56.dp, border = colors.line)
            }
            val remove = @Composable { modifier: Modifier ->
                PillButton("Remove", { actions.onRemove(sheet.id) }, colors.surface, colors.muted, modifier.fillMaxWidth(), height = 56.dp, border = colors.line)
            }
            if (largeText()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    makeHome(Modifier)
                    remove(Modifier)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    makeHome(Modifier.weight(1f))
                    remove(Modifier.weight(1f))
                }
            }
        }
    }
}

/** The next hours in six even columns, or three to a row with large text. */
@Composable
private fun NextHours(hours: List<SheetHour>) {
    val colors = MorrowTheme.colors
    val type = MorrowTheme.type
    val perRow = if (largeText()) 3 else 6
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        hours.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { hour ->
                    Column(
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = hour.spoken },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText(hour.time, style = type.caption.copy(color = colors.muted))
                        MorrowIcon(weatherIcon(hour.sky, hour.night), colors.hi, size = 22.dp)
                        BasicText(hour.temperature, style = type.bodyStrong.copy(color = colors.text))
                    }
                }
                // Keeps a short last row in the same columns as the rows above it.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
