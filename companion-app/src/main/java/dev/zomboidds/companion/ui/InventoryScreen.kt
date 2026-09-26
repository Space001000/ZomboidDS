package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.ContainerLayout
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Container
import dev.zomboidds.companion.domain.ContainerKind
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.foldWorn
import dev.zomboidds.companion.domain.labels
import dev.zomboidds.companion.domain.stacks
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fixed sizes, so a pane can show whole rows only (no tiles cut in half at the bottom).
internal val TileHeight = 66.dp
internal val RowHeight = 50.dp
internal val Gap = 5.dp
internal val PaneHeader = 36.dp
internal val PaneSpacing = 6.dp
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

    // Into your selected container, like the game's "Loot all" (not always the main inventory).
    fun takeAll(view: ContainerView) =
        ("Take all" to { moveAll(view, mineShown) }).takeIf { canMove && !view.container.locked && view.container.kind != ContainerKind.INVENTORY }

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
                // What's left for the two grids: minus both headers and the divider with its spacing. Each
                // grid is n rows plus (n - 1) gaps, hence the two gaps added back.
                val grids = maxHeight - (PaneHeader + PaneSpacing) * 2 - SplitDivider - SplitSpacing * 2 + Gap * 2
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
                Column(verticalArrangement = Arrangement.spacedBy(SplitSpacing)) {
                    ContainerPane(mine, mineShown, { mineOpen = it }, display.items, iconUrl, selection?.second, onItem,
                        allAction = putAll, modifier = Modifier.height(PaneHeader + PaneSpacing + row * mineRows - Gap), trailing = switch)
                    HorizontalDivider(thickness = SplitDivider)
                    if (aroundShown != null) {
                        ContainerPane(around, aroundShown, { aroundOpen = it; highlight(it) }, display.items, iconUrl, selection?.second, onItem,
                            allAction = takeAll(aroundShown), modifier = Modifier.weight(1f))
                    }
                }
            } else {
                ContainerPane(views, singleShown, { open(it); highlight(it) }, display.items, iconUrl, selection?.second, onItem,
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
            ItemPanel(
                stack, iconUrl,
                // The game's own menu; for items around you, the one its loot window shows (Grab, ...).
                loadMenu = { actions.itemMenu(stack.first.id) },
                onAction = { action -> run(itemName) { actions.perform(ItemCommand(stack.first.id, action)) } },
                moves = moves,
                moveTargets = if (canMove) views.filter { it.id != view.id && !it.container.locked } else emptyList(),
                onMoveTo = { target -> moveStack(stack, target) },
                onMenuOption = { menuId, optionId -> run(itemName) { actions.selectMenuOption(menuId, optionId) } },
                onClose = { selectedId = null },
            )
        }
    }
}
