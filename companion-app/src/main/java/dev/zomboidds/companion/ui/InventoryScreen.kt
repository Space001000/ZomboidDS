package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.ContainerLayout
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.Freshness
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.PaneEntry
import dev.zomboidds.companion.domain.foldWorn
import dev.zomboidds.companion.domain.isKeyRing
import dev.zomboidds.companion.domain.labels
import dev.zomboidds.companion.domain.stacks
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Danger = Color(0xFFE53935)
private val Good = Color(0xFF7CB342)
private val Worn = Color(0xFFE8C547)

// Fixed sizes, so a pane can show whole rows only (no tiles cut in half at the bottom).
private val TileHeight = 66.dp
private val RowHeight = 50.dp
private val Gap = 5.dp
private val PaneHeader = 36.dp
private val PaneSpacing = 6.dp

/** Open [containerId] (null: the main inventory). A new instance each time, so repeats apply again. */
class ShowRequest(val containerId: String?)

/** How the inventory tab is laid out: the user's choice, kept in AppSettings. */
data class InventoryDisplay(val items: InventoryLayout, val containers: ContainerLayout)

/** A container as shown. The main inventory's items and weight come from the `inventory` message. */
private class ContainerView(
    val container: Container,
    val stacks: List<ItemStack>,
    val weight: Float?,
    val capacity: Float?,
    /** The name, numbered when the game repeats it ("Shelves 2"). */
    val label: String = container.name,
) {
    val id: String get() = container.id

    /** Inventory and bags: the top half in the split layout. */
    val onPlayer: Boolean get() = container.kind == ContainerKind.INVENTORY || container.kind == ContainerKind.BAG
}

/** The main inventory first, then the rest in the game's order. Without `containers` (older mod), only the inventory. */
private fun containerViews(inventory: Inventory, containers: List<Container>?): List<ContainerView> {
    val main = containers?.firstOrNull { it.kind == ContainerKind.INVENTORY }
        ?: Container("inventory", ContainerKind.INVENTORY, "Inventory", null, null, null, locked = false, items = null)
    val others = containers.orEmpty().filter { it.kind != ContainerKind.INVENTORY }
    val labels = (listOf(main) + others).labels()
    return listOf(ContainerView(main, inventory.stacks(), inventory.weight, inventory.maxWeight, labels.getValue(main.id))) +
        others.map { ContainerView(it, it.items.orEmpty().stacks(), it.weight, it.capacity, labels.getValue(it.id)) }
}

@Composable
fun InventoryScreen(
    inventory: Inventory?,
    containers: List<Container>?,
    iconUrl: (String) -> String,
    actions: ItemActions,
    display: InventoryDisplay,
    onDisplayChange: (InventoryDisplay) -> Unit,
    show: ShowRequest? = null,
    onShowHandled: () -> Unit = {},
) {
    if (inventory == null) {
        Text("Waiting for inventory...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val views = remember(inventory, containers) { containerViews(inventory, containers) }
    // Moving between containers needs the containers message (and its ids).
    val canMove = containers != null
    val split = canMove && display.containers == ContainerLayout.SPLIT
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    // The open tab of each pane, by container id. A container that went out of reach falls back to the first.
    var mineOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var aroundOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var singleOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val main = views.first()
    val mine = views.filter { it.onPlayer }
    val around = views.filterNot { it.onPlayer }
    val mineShown = mine.firstOrNull { it.id == mineOpen } ?: main
    // Until you pick one, the bottom half shows what the game's loot window has selected (and outlines).
    val aroundShown = around.firstOrNull { it.id == aroundOpen }
        ?: around.firstOrNull { it.container.selected } ?: around.firstOrNull()
    val singleShown = views.firstOrNull { it.id == singleOpen } ?: main

    // The game asked for a container (Loot button): open its tab, in whichever half it belongs.
    LaunchedEffect(show) {
        if (show == null) return@LaunchedEffect
        val id = show.containerId ?: main.id
        onShowHandled()
        selectedId = null
        singleOpen = id
        if (views.firstOrNull { it.id == id }?.onPlayer == true) mineOpen = id else aroundOpen = id
    }

    // Follow the selected item through updates, wherever it is now; it's gone once used up or out of reach.
    val selection = views.firstNotNullOfOrNull { view ->
        view.stacks.firstOrNull { stack -> stack.items.any { it.id == selectedId } }?.let { view to it }
    }

    LaunchedEffect(failure) {
        if (failure != null) {
            delay(4_000)
            failure = null
        }
    }

    fun run(label: String, action: suspend () -> CommandResult) {
        selectedId = null
        scope.launch {
            val result = action()
            if (result is CommandResult.Failed) failure = "$label: ${result.reason}"
        }
    }

    // A stack moves as a whole, like dragging it in the game: one command per item, the game queues them.
    fun moveStack(stack: ItemStack, to: ContainerView) = run(stack.first.name) {
        var result: CommandResult = CommandResult.Ok
        for (item in stack.items) {
            result = actions.transfer(item.id, to.id)
            if (result is CommandResult.Failed) break
        }
        result
    }

    fun moveAll(from: ContainerView, to: ContainerView) = run(from.label) { actions.transferAll(from.id, to.id) }

    // Opening a container around you selects it in the game too, which outlines it in the world.
    fun highlight(id: String) {
        val view = views.firstOrNull { it.id == id } ?: return
        if (canMove && !view.onPlayer && !view.container.locked) {
            scope.launch { actions.selectContainer(id) }
        }
    }

    fun takeAll(view: ContainerView) =
        ("Take all" to { moveAll(view, main) }).takeIf { canMove && !view.container.locked && view.container.kind != ContainerKind.INVENTORY }

    Box(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The display toggles sit at the end of the first tab row, so they cost no screen height.
            val switch = @Composable { DisplaySwitch(display, onDisplayChange, showContainerLayout = canMove) }
            val onItem = { stack: ItemStack -> selectedId = stack.first.id }
            if (split) BoxWithConstraints {
                // The half with more to show gets more room, but each keeps at least about a third,
                // in whole rows: the grids' height is shared out row by row.
                val mineCount = mineShown.stacks.size.coerceAtLeast(1).toFloat()
                val aroundCount = (aroundShown?.stacks?.size ?: 0).coerceAtLeast(1).toFloat()
                val mineShare = (mineCount / (mineCount + aroundCount)).coerceIn(0.35f, 0.65f)
                val row = (if (display.items == InventoryLayout.GRID) TileHeight else RowHeight) + Gap
                val grids = maxHeight - (PaneHeader + PaneSpacing) * 2 - 2.dp - 16.dp + Gap * 2
                val rows = (grids / row).toInt().coerceAtLeast(2)
                // Neither half keeps rows it has nothing for: the other one gets them.
                val columns = ((maxWidth + Gap) / ((if (display.items == InventoryLayout.GRID) 64.dp else 200.dp) + Gap)).toInt().coerceAtLeast(1)
                fun rowsFor(view: ContainerView?) = ((view?.stacks?.foldWorn(open = false)?.size ?: 0) + columns - 1) / columns
                val mineNeed = rowsFor(mineShown).coerceAtLeast(1)
                val aroundNeed = rowsFor(aroundShown).coerceAtLeast(1)
                var mineRows = (rows * mineShare).roundToInt().coerceIn(1, rows - 1)
                if (mineNeed < mineRows) mineRows = mineNeed
                else if (aroundNeed < rows - mineRows) mineRows = minOf(mineNeed, rows - aroundNeed)
                val putAll = aroundShown?.takeIf { !it.container.locked }?.let { target ->
                    "Put all" to { moveAll(mineShown, target) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContainerPane(mine, mineShown, { mineOpen = it }, display.items, iconUrl, selection?.second, onItem,
                        allAction = putAll, modifier = Modifier.height(PaneHeader + PaneSpacing + row * mineRows - Gap), trailing = switch)
                    HorizontalDivider(thickness = 2.dp)
                    if (aroundShown != null) {
                        ContainerPane(around, aroundShown, { aroundOpen = it; highlight(it) }, display.items, iconUrl, selection?.second, onItem,
                            allAction = takeAll(aroundShown), modifier = Modifier.weight(1f))
                    }
                }
            } else {
                ContainerPane(views, singleShown, { singleOpen = it; highlight(it) }, display.items, iconUrl, selection?.second, onItem,
                    allAction = takeAll(singleShown), modifier = Modifier.weight(1f), trailing = switch)
            }
        }
        failure?.let {
            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(it, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selection != null) {
            val (view, stack) = selection
            // Drawn in this window, not as a dialog: a new window could take focus from the game.
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null) { selectedId = null },
            )
            // One-tap moves: out of a container into your inventory, and in the split layout to the other half.
            val moves = buildList<Pair<String, () -> Unit>> {
                if (!canMove) return@buildList
                if (view.container.kind != ContainerKind.INVENTORY) {
                    add((if (view.onPlayer) "Take out" else "Take") to { moveStack(stack, main) })
                }
                if (split && view.onPlayer && aroundShown != null && !aroundShown.container.locked) {
                    add("Put in ${aroundShown.label}" to { moveStack(stack, aroundShown) })
                }
            }
            val itemName = stack.first.name
            ActionPanel(
                stack, iconUrl,
                // The game's own menu; for items around you, the one its loot window shows (Grab, ...).
                loadMenu = { actions.itemMenu(stack.first.id) },
                onAction = { action -> run(itemName) { actions.perform(ItemCommand(stack.first.id, action)) } },
                moves = moves,
                moveTargets = if (canMove) views.filter { it.id != view.id && !it.container.locked } else emptyList(),
                onMoveTo = { target -> moveStack(stack, target) },
                onMenuOption = { menuId, optionId -> run(itemName) { actions.selectMenuOption(menuId, optionId) } },
                onClose = { selectedId = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Tabs for [tabs], the open container's header, and its items. */
@Composable
private fun ContainerPane(
    tabs: List<ContainerView>,
    shown: ContainerView,
    onOpen: (String) -> Unit,
    layout: InventoryLayout,
    iconUrl: (String) -> String,
    selected: ItemStack?,
    onItem: (ItemStack) -> Unit,
    allAction: Pair<String, () -> Unit>?,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    var wornOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaneSpacing)) {
        // One row: tabs (the open one with its weight), the "all" action, and [trailing].
        Row(Modifier.height(PaneHeader), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { ContainerTabs(tabs, shown, onOpen, iconUrl) }
            allAction?.takeIf { shown.stacks.isNotEmpty() }?.let { (label, onClick) -> AllButton(label, shown.id, onClick) }
            trailing?.let {
                Spacer(Modifier.width(8.dp)) // a little away from the "all" action: no mis-taps
                it()
            }
        }
        when {
            shown.container.locked -> Hint("Locked")
            shown.stacks.isEmpty() -> Hint("Empty")
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // Whole rows only: the grid is cut to the rows that fit.
                val itemHeight = if (layout == InventoryLayout.GRID) TileHeight else RowHeight
                val rows = ((maxHeight + Gap) / (itemHeight + Gap)).toInt().coerceAtLeast(1)
                val entries = remember(shown.stacks, wornOpen) { shown.stacks.foldWorn(wornOpen) }
                LazyVerticalGrid(
                    // Compact grid: ~7 columns on the Thor's bottom screen. List: 2 columns of rows.
                    columns = GridCells.Adaptive(minSize = if (layout == InventoryLayout.GRID) 64.dp else 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    verticalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier.fillMaxWidth().height((itemHeight + Gap) * rows - Gap),
                ) {
                    items(entries, key = { entry ->
                        when (entry) {
                            is PaneEntry.Stack -> entry.stack.first.id
                            is PaneEntry.Worn -> "worn"
                        }
                    }) { entry ->
                        when (entry) {
                            is PaneEntry.Stack -> if (layout == InventoryLayout.GRID) {
                                GridTile(entry.stack, iconUrl, selected = entry.stack == selected, worn = entry.worn, onClick = { onItem(entry.stack) })
                            } else {
                                ListRow(entry.stack, iconUrl, selected = entry.stack == selected, worn = entry.worn, onClick = { onItem(entry.stack) })
                            }
                            is PaneEntry.Worn -> WornTile(entry, iconUrl, list = layout == InventoryLayout.LIST, onClick = { wornOpen = !wornOpen })
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Put all" / "Take all": one mis-tap would move everything, so the first tap only arms it ("Put
 * all?", in red) and a second tap within 3 seconds does it.
 */
@Composable
private fun AllButton(label: String, containerId: String, onClick: () -> Unit) {
    var armed by remember(containerId) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    OutlinedButton(
        onClick = { if (armed) { armed = false; onClick() } else armed = true },
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(34.dp),
        colors = if (armed) ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else ButtonDefaults.outlinedButtonColors(),
        border = if (armed) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder(),
    ) {
        Text(if (armed) "$label?" else label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ContainerTabs(tabs: List<ContainerView>, shown: ContainerView, onOpen: (String) -> Unit, iconUrl: (String) -> String) {
    val scroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth()
            // More tabs than fit: the row fades out at the edge.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (scroll.canScrollForward) {
                    drawRect(
                        Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { view ->
            val open = view.id == shown.id
            // Only the open tab has its name; the others are the game's icon (and number, "Shelves 2").
            val compact = !open && view.container.icon != null
            val number = view.label.removePrefix(view.container.name).trim().takeIf { view.label != view.container.name }
            Surface(
                onClick = { onOpen(view.id) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.semantics { contentDescription = view.label },
                color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ContainerIcon(view.container, iconUrl, 20.dp)
                        if (compact) {
                            number?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        } else {
                            Text(view.label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                        }
                        if (open) WeightLabel(view)
                    }
                    if (open) WeightMeter(view)
                }
            }
        }
    }
}

private fun ContainerView.overloaded() = weight != null && capacity != null && capacity > 0f && weight > capacity

/** "3.5/10" for the open tab; red when over capacity. */
@Composable
private fun WeightLabel(view: ContainerView) {
    val weight = view.weight ?: return
    val capacity = view.capacity?.takeIf { it > 0f }
    Text(
        if (capacity != null) "%.1f/%.0f".format(weight, capacity) else "%.1f".format(weight),
        style = MaterialTheme.typography.labelSmall,
        color = if (view.overloaded()) Danger else LocalContentColor.current.copy(alpha = 0.8f),
        maxLines = 1,
    )
}

/** A thin fill bar along the bottom of the open tab. */
@Composable
private fun WeightMeter(view: ContainerView) {
    val weight = view.weight ?: return
    val capacity = view.capacity?.takeIf { it > 0f } ?: return
    val fraction = (weight / capacity).coerceIn(0f, 1f)
    val color = if (view.overloaded()) Danger else MaterialTheme.colorScheme.primary
    // Drawn rather than a progress indicator: this must not widen the tab (an indicator asks for 240dp).
    Box(Modifier.fillMaxWidth().height(3.dp).drawBehind {
        drawRect(color, size = Size(size.width * fraction, size.height))
    })
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ContainerIcon(container: Container, iconUrl: (String) -> String, size: androidx.compose.ui.unit.Dp) {
    container.icon?.let { icon ->
        AsyncImage(
            model = iconUrl(icon),
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(size),
        )
    }
}

/** Two small toggles showing the current modes; a tap switches to the other one. */
@Composable
private fun DisplaySwitch(display: InventoryDisplay, onChange: (InventoryDisplay) -> Unit, showContainerLayout: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showContainerLayout) {
            val split = display.containers == ContainerLayout.SPLIT
            ToggleIcon(if (split) "Split layout" else "Single layout", onClick = {
                onChange(display.copy(containers = if (split) ContainerLayout.SINGLE else ContainerLayout.SPLIT))
            }) { color ->
                if (split) {
                    drawRect(color, Offset(0f, 0f), Size(size.width, size.height * 0.44f), style = Stroke(2.dp.toPx()))
                    drawRect(color, Offset(0f, size.height * 0.56f), Size(size.width, size.height * 0.44f), style = Stroke(2.dp.toPx()))
                } else {
                    drawRect(color, style = Stroke(2.dp.toPx()))
                }
            }
        }
        val grid = display.items == InventoryLayout.GRID
        ToggleIcon(if (grid) "Grid view" else "List view", onClick = {
            onChange(display.copy(items = if (grid) InventoryLayout.LIST else InventoryLayout.GRID))
        }) { color ->
            val cell = size.width * 0.42f
            if (grid) {
                for (x in listOf(0f, size.width - cell)) for (y in listOf(0f, size.height - cell)) {
                    drawRect(color, Offset(x, y), Size(cell, cell))
                }
            } else {
                for (i in 0..2) drawRect(color, Offset(0f, size.height * (0.08f + i * 0.36f)), Size(size.width, size.height * 0.16f))
            }
        }
    }
}

@Composable
private fun ToggleIcon(description: String, onClick: () -> Unit, draw: DrawScope.(Color) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(34.dp).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.padding(9.dp).fillMaxSize()) { draw(color) }
    }
}

/**
 * Compact tile: icon first, the name on one line; everything else is in the action panel. In hand:
 * a small corner label. Condition: a thin bar only once it's damaged. [worn]: one of the unfolded
 * worn clothes.
 */
@Composable
private fun GridTile(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, worn: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected) ?: wornBorder(worn), modifier = Modifier.height(TileHeight)) {
        Box(Modifier.fillMaxSize().padding(3.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                    ItemIcon(item, iconUrl, 36.dp)
                    if (stack.count > 1) Badge("×${stack.count}", Modifier.align(Alignment.BottomEnd))
                }
                Text(
                    if (item.isKeyRing) "Keys" else item.name,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hand = handLabel(item.equipped)
            hand?.let { CornerLabel(it, Modifier.align(Alignment.TopStart)) }
            item.freshness?.let { FreshnessDot(it, Modifier.align(if (hand == null) Alignment.TopStart else Alignment.TopEnd).padding(3.dp)) }
            damage(item.condition)?.let { (fraction, color) ->
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 3.dp)) { Meter(fraction, color, 2.dp) }
            }
        }
    }
}

/** Your worn clothes folded into one tile (or row): a few of their icons and how many. */
@Composable
private fun WornTile(entry: PaneEntry.Worn, iconUrl: (String) -> String, list: Boolean, onClick: () -> Unit) {
    val label = "Worn ×${entry.stacks.size}"
    val icons = @Composable {
        Box(Modifier.size(width = 40.dp, height = 36.dp)) {
            entry.stacks.take(3).forEachIndexed { i, stack ->
                Box(Modifier.offset(x = (i * 8).dp, y = (i * 5).dp)) { ItemIcon(stack.first, iconUrl, 24.dp) }
            }
        }
    }
    Card(
        onClick = onClick,
        border = BorderStroke(1.dp, if (entry.open) Worn else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.height(if (list) RowHeight else TileHeight),
    ) {
        if (list) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icons()
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(if (entry.open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) { icons() }
                Text(label + if (entry.open) " ▴" else "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun wornBorder(worn: Boolean) = if (worn) BorderStroke(1.dp, Worn.copy(alpha = 0.5f)) else null

/** Only damaged items get a bar: green, yellow below 60 %, red below 30 %. */
private fun damage(condition: Float?): Pair<Float, Color>? {
    if (condition == null || condition >= 0.995f) return null
    return condition to when {
        condition < 0.3f -> Danger
        condition < 0.6f -> Worn
        else -> Good
    }
}

/** List row: readable names plus where the item is and what it weighs. */
@Composable
private fun ListRow(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, worn: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected) ?: wornBorder(worn), modifier = Modifier.height(RowHeight)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ItemIcon(item, iconUrl, 32.dp)
            item.freshness?.let { FreshnessDot(it, Modifier) }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    equippedLabel(item.equipped),
                    item.weight?.let { "%.1f".format(it * stack.count) },
                    damage(item.condition)?.let { "${(it.first * 100).toInt()}%" }, // 100 % is the norm: not shown
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (stack.count > 1) Badge("×${stack.count}", Modifier, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ItemIcon(item: InventoryItem, iconUrl: (String) -> String, size: androidx.compose.ui.unit.Dp) {
    item.icon?.let { icon ->
        AsyncImage(
            model = iconUrl(icon),
            contentDescription = item.name,
            filterQuality = FilterQuality.None, // pixel art: keep it crisp when scaled up
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun selectedBorder(selected: Boolean) =
    if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

@Composable
private fun Meter(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(height),
        color = color,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(
    stack: ItemStack,
    iconUrl: (String) -> String,
    /** Null: no game menu for this item. */
    loadMenu: (suspend () -> ItemMenuResult)?,
    onAction: (ItemAction) -> Unit,
    /** One-tap moves, shown first ("Take", "Put in Drawer"). */
    moves: List<Pair<String, () -> Unit>>,
    /** Every container the item could go to, behind "Move to…". */
    moveTargets: List<ContainerView>,
    onMoveTo: (ContainerView) -> Unit,
    onMenuOption: (menuId: String, optionId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val item = stack.first
    // The game's own menu for this item, fetched when the panel opens (one round trip).
    var reload by remember { mutableIntStateOf(0) }
    val menu by produceState<ItemMenuResult?>(null, item.id, reload) {
        value = null
        value = loadMenu?.invoke()
    }
    var choosingTarget by remember(item.id) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemIcon(item, iconUrl, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    val details = listOfNotNull(
                        equippedLabel(item.equipped),
                        item.category,
                        item.weight?.let { "weight %.2f".format(it) },
                        item.condition?.let { "condition ${(it * 100).toInt()}%" },
                        "×${stack.count}".takeIf { stack.count > 1 },
                    )
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
            // Where it goes first, then what you do with it.
            if (moves.isNotEmpty() || moveTargets.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moves.forEach { (label, onClick) -> FilledTonalButton(onClick = onClick) { Text(label) } }
                    if (moveTargets.isNotEmpty()) {
                        OutlinedButton(onClick = { choosingTarget = !choosingTarget }) { Text("Move to…") }
                    }
                }
            }
            if (choosingTarget) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moveTargets.forEach { target ->
                        OutlinedButton(onClick = { onMoveTo(target) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            ContainerIcon(target.container, iconUrl, 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(target.label)
                        }
                    }
                }
            }
            if (item.actions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.actions.forEach { action ->
                    if (action == ItemAction.DROP) {
                        OutlinedButton(onClick = { onAction(action) }) { Text(label(action, item)) }
                    } else {
                        Button(onClick = { onAction(action) }) { Text(label(action, item)) }
                    }
                }
            }
            if (loadMenu == null) return@Column
            HorizontalDivider()
            when (val result = menu) {
                null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading the game's menu...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ItemMenuResult.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.reason, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reload++ }) { Text("Retry") }
                }
                is ItemMenuResult.Ready -> GameMenu(result.menu, onSelect = { optionId -> onMenuOption(result.menu.menuId, optionId) })
            }
        }
    }
}

/** The game's right-click menu: submenus open in place, greyed options show the game's reason. */
@Composable
internal fun GameMenu(menu: ItemMenu, onSelect: (optionId: String) -> Unit) {
    var path by remember(menu.menuId) { mutableStateOf(listOf<MenuOption>()) }
    val options = path.lastOrNull()?.children ?: menu.options
    Column {
        if (path.isNotEmpty()) {
            TextButton(onClick = { path = path.dropLast(1) }) { Text("‹ " + path.joinToString(" › ") { it.name }) }
        }
        options.forEach { option ->
            val submenu = option.children.isNotEmpty()
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = option.enabled) { if (submenu) path = path + option else onSelect(option.id) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        option.name,
                        color = if (option.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    option.tooltip?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (submenu) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun label(action: ItemAction, item: InventoryItem) = when (action) {
    ItemAction.EQUIP_PRIMARY -> "Main hand"
    ItemAction.EQUIP_SECONDARY -> "Off hand"
    ItemAction.EQUIP_BOTH -> "Both hands"
    ItemAction.WEAR -> "Wear"
    ItemAction.UNEQUIP -> if (item.equipped == EquipSlot.WORN) "Take off" else "Unequip"
    ItemAction.DROP -> "Drop"
}

/** In hand, for the tile's corner (worn clothes are grouped instead). */
private fun handLabel(slot: EquipSlot?) = equippedLabel(slot).takeIf { slot != EquipSlot.WORN }

private fun equippedLabel(slot: EquipSlot?) = when (slot) {
    EquipSlot.PRIMARY -> "Main"
    EquipSlot.SECONDARY -> "Off"
    EquipSlot.BOTH -> "Both"
    EquipSlot.WORN -> "Worn"
    null -> null
}

@Composable
private fun Badge(text: String, modifier: Modifier, fontSize: TextUnit = 9.sp) {
    Text(
        text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** A quiet label in a tile's corner ("Main"): readable, without covering the icon like a badge. */
@Composable
private fun CornerLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        fontSize = 8.sp,
        maxLines = 1,
    )
}

/** Food: green fresh, yellow stale, red rotten (the game's name says it in words: "Stale Bread"). */
@Composable
private fun FreshnessDot(freshness: Freshness, modifier: Modifier) {
    val color = when (freshness) {
        Freshness.FRESH -> Good
        Freshness.STALE -> Worn
        Freshness.ROTTEN -> Danger
    }
    Box(modifier.size(8.dp).background(color, CircleShape).semantics { contentDescription = freshness.name.lowercase() })
}
