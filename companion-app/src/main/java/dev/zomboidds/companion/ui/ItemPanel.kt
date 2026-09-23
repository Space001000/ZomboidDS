package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.ItemStack

/** The panel for a tapped item: moves, quick actions and the game's own menu for it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.ItemPanel(
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
) = BottomPanel(onDismiss = onClose, spacing = 12.dp) {
    val item = stack.first
    var choosingTarget by remember(item.id) { mutableStateOf(false) }
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
    if (loadMenu != null) {
        HorizontalDivider()
        // The game's own menu for this item, fetched when the panel opens (one round trip).
        LoadingGameMenu(key = item.id, load = loadMenu, onSelect = onMenuOption)
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
