package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.ContainerLayout
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.PickedItems
import dev.zomboidds.companion.domain.ShownPicks
import dev.zomboidds.companion.domain.foldWorn
import dev.zomboidds.companion.domain.labels
import dev.zomboidds.companion.domain.stacks
import dev.zomboidds.companion.domain.toggle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fixed sizes, so a pane can show whole rows only (no tiles cut in half at the bottom).
internal val TileHeight = 66.dp
internal val RowHeight = 50.dp
internal val Gap = 5.dp
internal val PaneHeader = 36.dp
internal val PaneSpacing = 6.dp
/** The second header row of a side-by-side pane: the open container's name and weight, and its actions. */
internal val PaneSubHeader = 34.dp
private val SplitDivider = 2.dp
private val SplitSpacing = 8.dp

/** Open [containerId] (null: the main inventory). A new instance each time, so repeats apply again. */
class ShowRequest(val containerId: String?)

/** How the inventory tab is laid out: the user's choice, kept in AppSettings. */
data class InventoryDisplay(val items: InventoryLayout, val containers: ContainerLayout)

/** A container as shown. The main inventory's items and weight come from the `inventory` message. */
internal class ContainerView(
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
    // Two panes, yours and around you: top and bottom, or side by side.
    val split = canMove && display.containers != ContainerLayout.SINGLE
    val sideBySide = split && display.containers == ContainerLayout.SIDE_BY_SIDE
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var picked by remember { mutableStateOf<PickedItems?>(null) }
    var pickedOpen by remember { mutableStateOf(false) }
    var wornOpen by rememberSaveable { mutableStateOf(false) }
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

    // Opening a container opens it in both layouts: in Single, picking one of your bags also makes it
    // where "Take all" puts things, as in Split.
    fun open(id: String) {
        singleOpen = id
        if (views.firstOrNull { it.id == id }?.onPlayer == true) mineOpen = id else aroundOpen = id
    }

    // The game asked for a container (Loot button): open its tab, in whichever half it belongs.
    LaunchedEffect(show) {
        if (show == null) return@LaunchedEffect
        onShowHandled()
        selectedId = null
        open(show.containerId ?: main.id)
    }

    // What's picked, as it is now: gone with its container (another tab opened, out of reach) or
    // once none of it is left there.
    val shownIds = if (split) setOfNotNull(mineShown.id, aroundShown?.id) else setOf(singleShown.id)
    val pickedView = picked?.let { p -> views.firstOrNull { it.id == p.containerId && it.id in shownIds } }
    val pickedStacks = pickedView?.let { picked?.stacksIn(it.stacks) }.orEmpty()
    val shownPicks = ShownPicks(pickedView?.id, pickedStacks)
    LaunchedEffect(pickedStacks.isEmpty()) {
        if (pickedStacks.isEmpty()) {
            picked = null
            pickedOpen = false
        }
    }

    // Holding an item and letting go picks it (or leaves it out again); while something is picked
    // in a container, a tap there does the same. Otherwise a tap opens the item's panel.
    fun pick(stack: ItemStack, from: String) {
        picked = picked.toggle(stack, from)
    }
    fun tap(stack: ItemStack, from: String) {
        if (shownPicks.tapPicks(from)) pick(stack, from) else selectedId = stack.first.id
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
        picked = null
        pickedOpen = false
        scope.launch {
            val result = action()
            if (result is CommandResult.Failed) failure = "$label: ${result.reason}"
        }
    }

    // A stack moves as a whole, like dragging it in the game: one command per item, the game queues them.
    fun moveStacks(stacks: List<ItemStack>, to: ContainerView) = run(stacks.singleOrNull()?.first?.name ?: "${stacks.size} items") {
        var result: CommandResult = CommandResult.Ok
        for (item in stacks.flatMap { it.items }) {
            result = actions.transfer(item.id, to.id)
            if (result is CommandResult.Failed) break
        }
        result
    }

    fun moveAll(from: ContainerView, to: ContainerView) = run(from.label) { actions.transferAll(from.id, to.id) }

    // Dragging an item onto a container tab moves the whole stack there; a picked one takes
    // everything picked with it.
    val currentViews by rememberUpdatedState(views)
    val currentPicks by rememberUpdatedState(shownPicks)
    val drag = remember {
        ItemDrag(
            carried = { stack, from -> currentPicks.carried(stack, from) },
            onDrop = { dragged, to ->
                currentViews.firstOrNull { it.id == to && !it.container.locked }?.let { target -> moveStacks(dragged, target) }
            },
        )
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    // Opening a container around you selects it in the game too, which outlines it in the world.
    fun highlight(id: String) {
        val view = views.firstOrNull { it.id == id } ?: return
        if (canMove && !view.onPlayer && !view.container.locked) {
            scope.launch { actions.selectContainer(id) }
        }
    }

    // Into your selected container, like the game's "Loot all" (not always the main inventory).
    fun takeAll(view: ContainerView) =
        ("Take all" to { moveAll(view, mineShown) }).takeIf { canMove && !view.container.locked && view.container.kind != ContainerKind.INVENTORY }

    CompositionLocalProvider(LocalItemDrag provides drag) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The display toggles sit at the end of the first tab row, so they cost no screen height.
            val switch = @Composable { DisplaySwitch(display, onDisplayChange, showContainerLayout = canMove) }
            // What each pane passes on: taps, holds and its picked items.
            @Composable
            fun Pane(
                tabs: List<ContainerView>, shown: ContainerView, onOpen: (String) -> Unit, allAction: Pair<String, () -> Unit>?,
                modifier: Modifier, trailing: (@Composable () -> Unit)? = null,
            ) = ContainerPane(
                tabs, shown, onOpen, display.items, iconUrl, selection?.second, { tap(it, shown.id) },
                allAction = allAction, modifier = modifier, trailing = trailing,
                picked = picked?.idsIn(shown.id).orEmpty(),
                onHold = { pick(it, shown.id) },
                onOpenPicked = { pickedOpen = true },
                onClearPicked = { picked = null },
                onPickSet = { ids -> picked = PickedItems.of(shown.id, ids) },
                wornOpen = wornOpen, onWornOpenChange = { wornOpen = it },
                stackedHeader = sideBySide,
            )
            if (sideBySide) {
                // Yours on the left, around you on the right, each the full height.
                val putAll = aroundShown?.takeIf { !it.container.locked }?.let { target ->
                    "Put all" to { moveAll(mineShown, target) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SplitSpacing)) {
                    Pane(mine, mineShown, { mineOpen = it }, putAll, modifier = Modifier.weight(1f), trailing = switch)
                    VerticalDivider(thickness = SplitDivider)
                    if (aroundShown != null) {
                        Pane(around, aroundShown, { aroundOpen = it; highlight(it) }, takeAll(aroundShown), modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            } else if (split) BoxWithConstraints {
                // The half with more to show gets more room, but each keeps at least about a third,
                // in whole rows: the grids' height is shared out row by row.
                val mineCount = mineShown.stacks.size.coerceAtLeast(1).toFloat()
                val aroundCount = (aroundShown?.stacks?.size ?: 0).coerceAtLeast(1).toFloat()
                val mineShare = (mineCount / (mineCount + aroundCount)).coerceIn(0.35f, 0.65f)
                val row = (if (display.items == InventoryLayout.GRID) TileHeight else RowHeight) + Gap
                // What's left for the two grids: minus both headers and the divider with its spacing. Each
                // grid is n rows plus (n - 1) gaps, hence the two gaps added back.
                val grids = maxHeight - (PaneHeader + PaneSpacing) * 2 - SplitDivider - SplitSpacing * 2 + Gap * 2
                val rows = (grids / row).toInt().coerceAtLeast(2)
                // Neither half keeps rows it has nothing for: the other one gets them.
                val columns = ((maxWidth + Gap) / ((if (display.items == InventoryLayout.GRID) 64.dp else 200.dp) + Gap)).toInt().coerceAtLeast(1)
                // With the worn clothes as shown: unfolded, they need their rows too (2 per row in the list).
                fun rowsFor(view: ContainerView?) = ((view?.stacks?.foldWorn(open = wornOpen)?.size ?: 0) + columns - 1) / columns
                val mineNeed = rowsFor(mineShown).coerceAtLeast(1)
                val aroundNeed = rowsFor(aroundShown).coerceAtLeast(1)
                var mineRows = (rows * mineShare).roundToInt().coerceIn(1, rows - 1)
                if (mineNeed < mineRows) mineRows = mineNeed
                else if (aroundNeed < rows - mineRows) mineRows = minOf(mineNeed, rows - aroundNeed)
                val putAll = aroundShown?.takeIf { !it.container.locked }?.let { target ->
                    "Put all" to { moveAll(mineShown, target) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(SplitSpacing)) {
                    Pane(mine, mineShown, { mineOpen = it }, putAll,
                        modifier = Modifier.height(PaneHeader + PaneSpacing + row * mineRows - Gap), trailing = switch)
                    HorizontalDivider(thickness = SplitDivider)
                    if (aroundShown != null) {
                        Pane(around, aroundShown, { aroundOpen = it; highlight(it) }, takeAll(aroundShown), modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Pane(views, singleShown, { open(it); highlight(it) }, takeAll(singleShown), modifier = Modifier.weight(1f), trailing = switch)
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
        // The panel: for what's picked (opened from its chip), or for one tapped item.
        val pickedPanel = pickedView?.takeIf { pickedOpen && pickedStacks.isNotEmpty() }
        val group = pickedPanel != null
        val panelFor = when {
            pickedPanel != null -> pickedPanel to pickedStacks
            selection != null -> selection.first to listOf(selection.second)
            else -> null
        }
        if (panelFor != null) {
            val (view, stacks) = panelFor
            // Where it can go, one tap each: your containers, then the ones around you (the open one first).
            val targets = if (!canMove) MoveTargets(emptyList(), emptyList()) else {
                val open = views.firstOrNull { it.id == (if (split) aroundShown?.id else singleShown.id) && !it.onPlayer }
                    ?: aroundShown
                val others = views.filter { it.id != view.id && !it.container.locked }
                val around = others.filterNot { it.onPlayer }
                MoveTargets(
                    yours = others.filter { it.onPlayer },
                    around = listOfNotNull(open?.takeIf { it in around }) + around.filter { it.id != open?.id },
                    open = open,
                )
            }
            val name = stacks.singleOrNull()?.first?.name ?: "${stacks.size} items"
            val ids = stacks.map { it.first.id }
            ItemPanel(
                stacks, iconUrl,
                // The game's own menu; for items around you, the one its loot window shows (Grab, ...).
                loadMenu = { actions.itemMenu(ids) },
                // For several, only what they all have (the game's menu has the rest).
                appActions = if (stacks.size == 1) {
                    stacks.first().first.actions
                } else {
                    listOf(ItemAction.DROP).filter { action -> stacks.all { action in it.first.actions } }
                },
                onAction = { action ->
                    run(name) {
                        var result: CommandResult = CommandResult.Ok
                        for (id in ids) {
                            result = actions.perform(ItemCommand(id, action))
                            if (result is CommandResult.Failed) break
                        }
                        result
                    }
                },
                targets = targets,
                onMoveTo = { target -> moveStacks(stacks, target) },
                onMenuOption = { menuId, optionId -> run(name) { actions.selectMenuOption(menuId, optionId) } },
                onClose = { if (group) pickedOpen = false else selectedId = null },
                onUnpick = if (group) { stack -> pick(stack, view.id) } else null,
            )
        }
        // The dragged item, under the finger; several picked ones as a little pile with how many.
        drag.stack?.let { dragged ->
            val at = drag.position - origin
            val count = drag.stacks.size
            Box(Modifier.offset { IntOffset((at.x - FloatHalfSize.toPx()).roundToInt(), (at.y - FloatHalfSize.toPx()).roundToInt()) }
                .graphicsLayer { rotationZ = -4f; shadowElevation = 12f }) {
                if (count > 1) {
                    Box(Modifier.offset(6.dp, 6.dp).size(FloatHalfSize * 2)) {
                        GridTile(drag.stacks[1], iconUrl, selected = true, worn = false, onClick = {})
                    }
                }
                Box(Modifier.size(FloatHalfSize * 2)) {
                    GridTile(dragged, iconUrl, selected = true, worn = false, onClick = {})
                    if (count > 1) Badge("×$count", Modifier.align(Alignment.TopEnd).padding(3.dp), fontSize = 12.sp)
                }
            }
        }
    }
    }
}

/** The floating item while dragging: a grid tile's size, centred on the finger. */
private val FloatHalfSize = 32.dp
