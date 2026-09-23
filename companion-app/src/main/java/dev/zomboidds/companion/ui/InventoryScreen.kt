package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.InventoryLayout
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemActions
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.stacks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Danger = Color(0xFFE53935)
private val Good = Color(0xFF7CB342)

@Composable
fun InventoryScreen(
    inventory: Inventory?,
    iconUrl: (String) -> String,
    actions: ItemActions,
    layout: InventoryLayout,
    onLayoutChange: (InventoryLayout) -> Unit,
) {
    if (inventory == null) {
        Text("Waiting for inventory...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val stacks = remember(inventory) { inventory.stacks() }
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Follow the item through inventory updates; it's gone once dropped.
    val selected = stacks.firstOrNull { stack -> stack.items.any { it.id == selectedId } }

    LaunchedEffect(failure) {
        if (failure != null) {
            delay(4_000)
            failure = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WeightBar(inventory) }
                Spacer(Modifier.width(12.dp))
                LayoutSwitch(layout, onLayoutChange)
            }
            failure?.let { Text(it, color = Color(0xFFE57373)) }
            LazyVerticalGrid(
                // Compact grid: ~6 columns on the Thor's bottom screen. List: 2 columns of rows.
                columns = GridCells.Adaptive(minSize = if (layout == InventoryLayout.GRID) 64.dp else 200.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(stacks, key = { it.first.id }) { stack ->
                    val onClick = { selectedId = stack.first.id }
                    if (layout == InventoryLayout.GRID) {
                        GridTile(stack, iconUrl, selected = stack == selected, onClick = onClick)
                    } else {
                        ListRow(stack, iconUrl, selected = stack == selected, onClick = onClick)
                    }
                }
            }
        }
        if (selected != null) {
            // Drawn in this window, not as a dialog: a new window could take focus from the game.
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null) { selectedId = null },
            )
            val itemName = selected.first.name
            fun run(action: suspend () -> CommandResult) {
                selectedId = null
                scope.launch {
                    val result = action()
                    if (result is CommandResult.Failed) failure = "$itemName: ${result.reason}"
                }
            }
            ActionPanel(
                selected, iconUrl,
                loadMenu = { actions.itemMenu(selected.first.id) },
                onAction = { action -> run { actions.perform(ItemCommand(selected.first.id, action)) } },
                onMenuOption = { menuId, optionId -> run { actions.selectMenuOption(menuId, optionId) } },
                onClose = { selectedId = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun LayoutSwitch(layout: InventoryLayout, onLayoutChange: (InventoryLayout) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        InventoryLayout.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = layout == option,
                onClick = { onLayoutChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, InventoryLayout.entries.size),
                icon = {},
            ) {
                Text(if (option == InventoryLayout.GRID) "Grid" else "List")
            }
        }
    }
}

@Composable
private fun WeightBar(inventory: Inventory) {
    val weight = inventory.weight ?: return
    val max = inventory.maxWeight?.takeIf { it > 0f }
    val overloaded = max != null && weight > max
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("Weight", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (max != null) "%.1f / %.0f".format(weight, max) else "%.1f".format(weight),
                color = if (overloaded) Danger else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (max != null) {
            Meter((weight / max), if (overloaded) Danger else MaterialTheme.colorScheme.primary, 6.dp)
        }
    }
}

/** Compact tile: icon first, the name on one line; everything else is in the action panel. */
@Composable
private fun GridTile(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected)) {
        Column(
            Modifier.fillMaxWidth().padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                ItemIcon(item, iconUrl, 36.dp)
                equippedLabel(item.equipped)?.let { Badge(it, Modifier.align(Alignment.TopEnd)) }
                if (stack.count > 1) Badge("×${stack.count}", Modifier.align(Alignment.BottomEnd))
            }
            Text(
                item.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.condition?.let { Meter(it, if (it < 0.3f) Danger else Good, 2.dp) }
        }
    }
}

/** List row: readable names plus where the item is and what it weighs. */
@Composable
private fun ListRow(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(onClick = onClick, border = selectedBorder(selected)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ItemIcon(item, iconUrl, 32.dp)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    equippedLabel(item.equipped),
                    item.weight?.let { "%.1f".format(it * stack.count) },
                    item.condition?.let { "${(it * 100).toInt()}%" },
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
    loadMenu: suspend () -> ItemMenuResult,
    onAction: (ItemAction) -> Unit,
    onMenuOption: (menuId: String, optionId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val item = stack.first
    // The game's own menu for this item, fetched when the panel opens (one round trip).
    var reload by remember { mutableIntStateOf(0) }
    val menu by produceState<ItemMenuResult?>(null, item.id, reload) {
        value = null
        value = loadMenu()
    }
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.actions.forEach { action ->
                    if (action == ItemAction.DROP) {
                        OutlinedButton(onClick = { onAction(action) }) { Text(label(action, item)) }
                    } else {
                        Button(onClick = { onAction(action) }) { Text(label(action, item)) }
                    }
                }
            }
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
private fun GameMenu(menu: ItemMenu, onSelect: (optionId: String) -> Unit) {
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
