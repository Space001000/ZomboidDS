package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.Inventory
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemCommand
import dev.zomboidds.companion.domain.ItemStack
import dev.zomboidds.companion.domain.stacks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(
    inventory: Inventory?,
    iconUrl: (String) -> String,
    perform: suspend (ItemCommand) -> CommandResult,
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WeightBar(inventory)
            failure?.let { Text(it, color = Color(0xFFE57373)) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(stacks, key = { it.first.id }) { stack ->
                    ItemTile(stack, iconUrl, selected = stack == selected, onClick = { selectedId = stack.first.id })
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
            ActionPanel(
                selected, iconUrl,
                onAction = { action ->
                    val command = ItemCommand(selected.first.id, action)
                    selectedId = null
                    scope.launch {
                        val result = perform(command)
                        if (result is CommandResult.Failed) failure = "${selected.first.name}: ${result.reason}"
                    }
                },
                onClose = { selectedId = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(
    stack: ItemStack,
    iconUrl: (String) -> String,
    onAction: (ItemAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val item = stack.first
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item.icon?.let {
                    AsyncImage(iconUrl(it), item.name, Modifier.size(48.dp), filterQuality = FilterQuality.None)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    val details = listOfNotNull(
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
            if (item.actions.isEmpty()) {
                Text("Nothing to do with this item from here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                color = if (overloaded) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (max != null) {
            LinearProgressIndicator(
                progress = { (weight / max).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (overloaded) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

@Composable
private fun ItemTile(stack: ItemStack, iconUrl: (String) -> String, selected: Boolean, onClick: () -> Unit) {
    val item = stack.first
    Card(
        onClick = onClick,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(64.dp)) {
                item.icon?.let { icon ->
                    AsyncImage(
                        model = iconUrl(icon),
                        contentDescription = item.name,
                        filterQuality = FilterQuality.None, // pixel art: keep it crisp when scaled up
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                equippedLabel(item.equipped)?.let { Badge(it, Modifier.align(Alignment.TopEnd)) }
                if (stack.count > 1) Badge("×${stack.count}", Modifier.align(Alignment.BottomEnd))
            }
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.condition?.let { condition ->
                LinearProgressIndicator(
                    progress = { condition.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = if (condition < 0.3f) Color(0xFFE53935) else Color(0xFF7CB342),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
    }
}

private fun equippedLabel(slot: EquipSlot?) = when (slot) {
    EquipSlot.PRIMARY -> "Main"
    EquipSlot.SECONDARY -> "Off"
    EquipSlot.BOTH -> "Both"
    EquipSlot.WORN -> "Worn"
    null -> null
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}
