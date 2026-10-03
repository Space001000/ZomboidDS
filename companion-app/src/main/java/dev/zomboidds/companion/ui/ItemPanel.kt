package dev.zomboidds.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.EquipSlot
import dev.zomboidds.companion.domain.InventoryItem
import dev.zomboidds.companion.domain.ItemAction
import dev.zomboidds.companion.domain.ItemMenu
import dev.zomboidds.companion.domain.ItemMenuResult
import dev.zomboidds.companion.domain.MenuOption
import dev.zomboidds.companion.domain.MenuPill
import dev.zomboidds.companion.domain.ItemStack

/** Where a tapped item can be moved: your own containers, then the ones around you. */
internal class MoveTargets(
    /** Your inventory, bags and key ring, without the one the item is in. */
    val yours: List<ContainerView>,
    /** Around you, the one open in the bottom half first ([open]). */
    val around: List<ContainerView>,
    val open: ContainerView? = null,
) {
    val isEmpty: Boolean get() = yours.isEmpty() && around.isEmpty()
}

/** Two columns from this width (the Thor, tablets); one on a narrow phone. */
private val TwoColumnWidth = 480.dp

/**
 * The panel for a tapped item, or for several picked together. Left: where it can go, one tap
 * each. Right: what you can do with it: the game's own menu for it (or the app's own buttons until
 * that's there), and for several, the list of them. Each side scrolls on its own, so a busy
 * kitchen and a long menu don't crowd each other.
 */
@Composable
internal fun BoxScope.ItemPanel(
    stacks: List<ItemStack>,
    iconUrl: (String) -> String,
    /** Null: no game menu for this item. */
    loadMenu: (suspend () -> ItemMenuResult)?,
    /** The app's own buttons, for while the game's menu isn't there. */
    appActions: List<ItemAction>,
    onAction: (ItemAction) -> Unit,
    targets: MoveTargets,
    onMoveTo: (ContainerView) -> Unit,
    onMenuOption: (menuId: String, optionId: String) -> Unit,
    onClose: () -> Unit,
    /** For picked items: leave one out. */
    onUnpick: ((ItemStack) -> Unit)? = null,
) {
    val item = stacks.first().first
    val key = stacks.map { it.first.id }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = null, indication = null, onClick = onClose),
    )
    Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.92f)
        // Taps on the panel stay on the panel.
        .clickable(interactionSource = null, indication = null, onClick = {})) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stacks.size == 1) Header(stacks.first(), iconUrl, onClose) else GroupHeader(stacks, iconUrl, onClose)
            BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth >= TwoColumnWidth) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!targets.isEmpty) {
                            Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MoveColumn(targets, iconUrl, onMoveTo)
                            }
                            VerticalDivider()
                        }
                        Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Actions(item, key, loadMenu, appActions, onAction, onMenuOption)
                            onUnpick?.let { PickedList(stacks, iconUrl, it) }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!targets.isEmpty) MoveColumn(targets, iconUrl, onMoveTo)
                        HorizontalDivider()
                        Actions(item, key, loadMenu, appActions, onAction, onMenuOption)
                        onUnpick?.let { PickedList(stacks, iconUrl, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(stack: ItemStack, iconUrl: (String) -> String, onClose: () -> Unit) {
    val item = stack.first
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ItemIcon(item, iconUrl, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(itemNameText(item), style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(
                equippedLabel(item.equipped),
                item.category,
                item.weight?.let { "weight %.2f".format(it) },
                item.condition?.let { "condition ${(it * 100).toInt()}%" },
                "×${stack.count}".takeIf { stack.count > 1 },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // While it heats: the game's cooking bar. A fluid container: the game's tooltip lines.
            item.cooking?.progress?.let { progress ->
                MeterLine(if (item.cooking.burning) "Burning" else "Cooking", progress, if (item.cooking.burning) Danger else Good, null)
            }
            item.fluid?.let { fluid ->
                val color = fluid.color?.let { (r, g, b) -> Color(r, g, b) } ?: MaterialTheme.colorScheme.primary
                MeterLine(if (fluid.mixture) "Mixture" else fluid.name ?: "Empty", fluid.fraction, color, fluid.amountText())
            }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** "Water ▬▬▬── 0.3 / 0.6 L": a label, a meter and an optional value, in the panel's header. */
@Composable
private fun MeterLine(label: String, fraction: Float, color: Color, value: String?) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FillMeter(fraction, color, Modifier.width(120.dp))
        value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Several items: a few of their icons, how many, and what they weigh together. */
@Composable
private fun GroupHeader(stacks: List<ItemStack>, iconUrl: (String) -> String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            stacks.take(4).forEach { stack -> ItemIcon(stack.first, iconUrl, 36.dp) }
        }
        Column(Modifier.weight(1f)) {
            Text("${stacks.size} items", style = MaterialTheme.typography.titleMedium)
            val weight = stacks.sumOf { stack -> stack.items.sumOf { (it.weight ?: 0f).toDouble() } }
            Text("weight %.2f".format(weight), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** The picked items, each with a ✕ to leave it out. */
@Composable
private fun ColumnScope.PickedList(stacks: List<ItemStack>, iconUrl: (String) -> String, onUnpick: (ItemStack) -> Unit) {
    HorizontalDivider()
    stacks.forEach { stack ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ItemIcon(stack.first, iconUrl, 28.dp)
            Text(stack.first.name + if (stack.count > 1) " ×${stack.count}" else "", Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = { onUnpick(stack) }, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(36.dp)) { Text("✕") }
        }
    }
}

/** "Move to": your containers, a divider, then the ones around you (the open one outlined). */
@Composable
private fun ColumnScope.MoveColumn(targets: MoveTargets, iconUrl: (String) -> String, onMoveTo: (ContainerView) -> Unit) {
    Text("Move to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    targets.yours.forEach { TargetButton(it, iconUrl, outlined = false, onMoveTo) }
    if (targets.yours.isNotEmpty() && targets.around.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
    }
    targets.around.forEach { TargetButton(it, iconUrl, outlined = it.id == targets.open?.id, onMoveTo) }
}

@Composable
private fun TargetButton(target: ContainerView, iconUrl: (String) -> String, outlined: Boolean, onMoveTo: (ContainerView) -> Unit) {
    Surface(
        onClick = { onMoveTo(target) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (outlined) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ContainerIcon(target.container, iconUrl, 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(target.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * What you can do with the item: the game's menu for it (fetched when the panel opens), its main
 * uses as pills on top and the rest below in the game's order. Until it's there, or when the game
 * has none (paused), the app's own buttons.
 */
@Composable
private fun ColumnScope.Actions(
    item: InventoryItem,
    key: Any,
    loadMenu: (suspend () -> ItemMenuResult)?,
    appActions: List<ItemAction>,
    onAction: (ItemAction) -> Unit,
    onMenuOption: (menuId: String, optionId: String) -> Unit,
) {
    if (loadMenu == null) {
        AppActions(item, appActions, onAction)
        return
    }
    LoadingGameMenu(
        key = key, load = loadMenu, onSelect = onMenuOption,
        untilReady = { AppActions(item, appActions, onAction) },
    ) { menu, select ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val (pills, rest) = menu.options.partition { it.pill != null }
            // The game leaves Drop out while a controller is in use (the Thor): then the app's.
            val appDrop = ItemAction.DROP.takeIf { it in appActions && pills.none { p -> p.pill == MenuPill.DROP } }
            Pills(menu, pills, select) {
                if (appDrop != null) OutlinedButton(onClick = { onAction(appDrop) }) { Text(label(appDrop, item)) }
            }
            if (rest.isNotEmpty()) {
                if (pills.isNotEmpty() || appDrop != null) HorizontalDivider()
                GameMenu(menu, select, top = rest)
            }
        }
    }
}

/** The app's own buttons (equip, wear, drop, ...), for when the game's menu isn't there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppActions(item: InventoryItem, actions: List<ItemAction>, onAction: (ItemAction) -> Unit) {
    if (actions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            if (action == ItemAction.DROP) {
                OutlinedButton(onClick = { onAction(action) }) { Text(label(action, item)) }
            } else {
                Button(onClick = { onAction(action) }) { Text(label(action, item)) }
            }
        }
    }
}

/**
 * The item's main uses from the game's menu, with the game's names, Drop last ([last] after it).
 * A pill with a submenu (Eat, Apply Bandage, Attach) unfolds the game's choices below the pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(menu: ItemMenu, pills: List<MenuOption>, onSelect: (optionId: String) -> Unit, last: @Composable () -> Unit) {
    var path by remember(menu.menuId) { mutableStateOf(listOf<MenuOption>()) }
    val (drops, uses) = pills.partition { it.pill == MenuPill.DROP }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uses.forEach { pill ->
            val open = path.firstOrNull()?.id == pill.id
            Pill(pill, open, outlined = false) {
                when {
                    open -> path = emptyList()
                    pill.children.isNotEmpty() -> path = listOf(pill)
                    else -> onSelect(pill.id)
                }
            }
        }
        drops.forEach { pill -> Pill(pill, open = false, outlined = true) { onSelect(pill.id) } }
        last()
    }
    val choices = path.lastOrNull()?.children ?: return
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (path.size > 1) {
                TextButton(onClick = { path = path.dropLast(1) }) { Text("‹ " + path.joinToString(" › ") { it.name }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    Pill(choice, open = false, outlined = true) {
                        if (choice.children.isNotEmpty()) path = path + choice else onSelect(choice.id)
                    }
                }
            }
            // A greyed choice can't be tapped: show the game's reason.
            choices.firstNotNullOfOrNull { choice -> choice.tooltip?.takeIf { !choice.enabled } }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Pill(option: MenuOption, open: Boolean, outlined: Boolean, onClick: () -> Unit) {
    val text = when {
        option.children.isEmpty() -> option.name
        open -> option.name + " ▾"
        else -> option.name + " ›"
    }
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = option.enabled) { Text(text) }
    } else {
        Button(onClick = onClick, enabled = option.enabled) { Text(text) }
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
