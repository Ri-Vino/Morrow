package app.morrow.ui.world

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
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
import app.morrow.ui.components.PullToRefresh
import app.morrow.ui.components.largeText
import app.morrow.ui.components.pressable
import app.morrow.ui.components.weatherIcon
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    /** Fetches the weather again, from pulling the cities down; null where there's none to fetch. */
    val onRefresh: (() -> Unit)? = null,
)

/** The World tab: a card for each saved city, and the sheet for [sheet] when a card has been tapped. */
@Composable
fun WorldScreen(
    model: WorldModel,
    sheet: CitySheet?,
    actions: WorldActions,
    modifier: Modifier = Modifier,
    /** The weather is being fetched again after a pull. */
    refreshing: Boolean = false,
) {
    Box(modifier.fillMaxSize()) {
        // Behind the sheet, the cities are hidden from TalkBack as well as dimmed.
        val hidden = Modifier.semantics { if (sheet != null) hideFromAccessibility() }
        val refresh = actions.onRefresh
        if (refresh == null) {
            WorldContent(model, actions, hidden)
        } else {
            PullToRefresh(refreshing, refresh, label = "Refresh the weather", modifier = hidden) { WorldContent(model, actions, Modifier) }
        }
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
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
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
 * The cities, home first. Any other city can be held and dragged among the cities below home, or moved by TalkBack's
 * "Move up" and "Move down". The held card lifts and follows the finger while the others slide out of its way, and the
 * new order is saved when it's let go.
 */
@Composable
private fun CityCards(cards: List<WorldCard>, actions: WorldActions) {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(cards)
    val latestActions by rememberUpdatedState(actions)
    var held by remember { mutableStateOf<Long?>(null) }
    // The card gliding into place after it's let go, which stays over the others until it lands.
    var landing by remember { mutableStateOf<Long?>(null) }
    // The order the finger has made, kept from the pick-up until the saved order catches up with it.
    var order by remember { mutableStateOf<List<Long>?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // Where each card was last laid out in the column, and where the drag counts it as being, which runs a frame ahead.
    val laid = remember { mutableMapOf<Long, Float>() }
    val places = remember { mutableMapOf<Long, Place>() }
    val slides = remember { mutableMapOf<Long, Slide>() }
    val gap = with(LocalDensity.current) { 12.dp.toPx() }

    val ids = cards.map { it.id }
    LaunchedEffect(ids) { if (held == null) order = null }
    val shown = order?.takeIf { it.toSet() == ids.toSet() }?.map { id -> cards.first { it.id == id } } ?: cards

    // Swaps with a neighbour once the held card's middle passes the neighbour's, never going above home.
    val drag = drag@{ dy: Float ->
        val id = held ?: return@drag
        val now = order ?: return@drag
        val me = places[id] ?: return@drag
        offset += dy
        val index = now.indexOf(id)
        val middle = me.top + offset + me.height / 2
        val belowId = now.getOrNull(index + 1)
        val aboveId = now.getOrNull(index - 1)?.takeIf { up -> latest.none { it.id == up && it.isHome } }
        val below = belowId?.let { places[it] }
        val above = aboveId?.let { places[it] }
        val (by, other) = when {
            below != null && middle > below.middle -> 1 to belowId
            above != null && middle < above.middle -> -1 to aboveId
            else -> return@drag
        }
        order = now.toMutableList().apply { add(index + by, removeAt(index)) }
        // Counts the new places in straight away, so the card stays under the finger and the swap can't undo itself
        // before the column is laid out again.
        val them = places.getValue(other)
        val (myTop, theirTop) = if (by > 0) (me.top + them.height + gap) to me.top else them.top to (them.top + me.height + gap)
        offset -= myTop - me.top
        places[id] = me.copy(top = myTop)
        places[other] = them.copy(top = theirTop)
        // The neighbour starts its slide from where it is now in the same frame as it moves, so it never shows in its
        // new place first.
        laid[id] = myTop
        laid[other] = theirTop
        slides[other]?.nudge(them.top - theirTop)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    shown.forEach { card ->
        key(card.id) {
            val isHeld = held == card.id
            val scope = rememberCoroutineScope()
            val slide = remember { Slide(scope) }
            DisposableEffect(card.id) {
                slides[card.id] = slide
                onDispose { slides.remove(card.id) }
            }
            val lift by animateFloatAsState(if (isHeld) 1.03f else 1f)
            CityCard(
                name = card.name,
                isHome = card.isHome,
                relative = card.relative,
                clock = card.clock,
                minuteOfDay = card.minute,
                weather = card.weather,
                lifted = isHeld,
                // Letting go of a held card isn't a tap.
                onClick = { if (held != card.id) actions.onOpenCity(card.id) },
                modifier = Modifier
                    .zIndex(if (isHeld || landing == card.id) 1f else 0f)
                    .onPlaced { coordinates ->
                        val top = coordinates.positionInParent().y
                        if (held == card.id) places[card.id]?.let { offset -= top - it.top }
                        places[card.id] = Place(top, coordinates.size.height.toFloat())
                        val before = laid.put(card.id, top)
                        // Slides from where it was into its new place, unless the drag has already started it.
                        if (before != null && abs(before - top) > 0.5f && held != card.id) slide.nudge(before - top)
                    }
                    // Gives the position above its own place in the column, without the drag or slide drawn below.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        translationY = if (held == card.id) offset else slide.value
                        scaleX = lift
                        scaleY = lift
                    }
                    .semantics {
                        customActions = listOfNotNull(
                            if (card.canMoveUp) CustomAccessibilityAction("Move up") { actions.onMove(card.id, -1); true } else null,
                            if (card.canMoveDown) CustomAccessibilityAction("Move down") { actions.onMove(card.id, 1); true } else null,
                        )
                    }
                    .then(
                        if (card.isHome) {
                            Modifier
                        } else {
                            Modifier.pointerInput(card.id) {
                                var from = 0
                                val drop: () -> Unit = {
                                    val final = order
                                    if (final != null) {
                                        val by = final.indexOf(card.id) - from
                                        if (by != 0) latestActions.onMove(card.id, by)
                                    }
                                    // Glides the rest of the way into its place, starting from where the finger left it.
                                    landing = card.id
                                    slide.from(offset) { if (landing == card.id) landing = null }
                                    held = null
                                    offset = 0f
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val saved = latest.map { it.id }
                                        val start = order?.takeIf { it.toSet() == saved.toSet() } ?: saved
                                        order = start
                                        from = start.indexOf(card.id)
                                        offset = slide.value
                                        slide.stop()
                                        held = card.id
                                    },
                                    onDrag = { pointer, amount ->
                                        pointer.consume()
                                        drag(amount.y)
                                    },
                                    onDragEnd = drop,
                                    onDragCancel = drop,
                                )
                            }
                        },
                    ),
            )
        }
    }
}

/** How far a card is drawn from its place while it slides into it: set at once, then eased back to nothing. */
private class Slide(private val scope: CoroutineScope) {
    var value by mutableFloatStateOf(0f)
        private set
    private var easing: Job? = null

    /** Moves the card [by] more at once, then eases it into its place. */
    fun nudge(by: Float) = from(value + by)

    fun from(start: Float, onSettled: () -> Unit = {}) {
        easing?.cancel()
        value = start
        easing = scope.launch {
            animate(start, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { now, _ -> value = now }
            onSettled()
        }
    }

    fun stop() {
        easing?.cancel()
        value = 0f
    }
}

/** A card's top and height in the column. */
private data class Place(val top: Float, val height: Float) {
    val middle: Float get() = top + height / 2
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
